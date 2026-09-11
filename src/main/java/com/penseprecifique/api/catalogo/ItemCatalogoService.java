package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoPreviewRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoPrecoSugeridoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ItemCatalogoMapper;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ItemCatalogoService {

    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoCustomizacaoRepository customizacaoRepository;
    private final CatalogoRepository catalogoRepository;
    private final ProdutoRepository produtoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final ItemCatalogoMapper itemCatalogoMapper;
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ItemCatalogoResponse> listarPorCatalogo(UUID catalogoId) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Catalogo catalogo = catalogoRepository.findByIdAndUsuarioId(catalogoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));
        return itemCatalogoRepository.findByCatalogoIdAndDeletedAtIsNull(catalogo.getId()).stream()
                .map(this::montarResponse)
                .toList();
    }

    /**
     * RN-044/045/046 — busca de itens de catálogo para a Seção Itens do orçamento.
     * Diferente de {@code buscarItemCatalogoParaVenda} (validação ao adicionar um item específico),
     * aqui o bloqueio é aplicado como filtro de listagem: o item simplesmente não aparece na busca.
     * RN-NOVA-6 (#217) — `busca` filtra por nome do produto, server-side (mesmo padrão de
     * ProdutoService#listar); em branco/nulo, retorna a listagem completa.
     * RN-NOVA-18 (#353/P-B008) — devolve a {@code Page<>} completa (não só o conteúdo) — contrato
     * HTTP passa a expor paginação real (`content`, `number`, `size`, `totalElements`, `last`),
     * mesmo formato já usado por {@code OrcamentoService#listar}.
     */
    @Transactional(readOnly = true)
    public Page<ItemCatalogoBuscaResponse> buscarParaOrcamento(UUID catalogoId, String busca, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        boolean temBusca = busca != null && !busca.isBlank();
        Page<ItemCatalogo> itens = temBusca
                ? itemCatalogoRepository.buscarDisponiveisParaOrcamentoComBusca(usuarioId, catalogoId, busca.trim(), pageable)
                : itemCatalogoRepository.buscarDisponiveisParaOrcamento(usuarioId, catalogoId, pageable);
        return itens.map(item -> itemCatalogoMapper.toBuscaResponse(item,
                fichaTecnicaItemRepository.findByProdutoId(item.getProduto().getId())));
    }

    /**
     * CAT-013 — preview ao vivo do preço sugerido de um item de catálogo (produto + quantidade de pacote +
     * customizações anexadas), sem persistir nada. Mesmo cálculo de {@link #calcularPrecoSugerido}, já usado em
     * adicionar/editar — aqui exposto isoladamente para a tela de Novo/Editar Item recalcular a cada mudança.
     */
    @Transactional(readOnly = true)
    public ItemCatalogoPrecoSugeridoResponse previewPreco(UUID catalogoId, ItemCatalogoPreviewRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        catalogoRepository.findByIdAndUsuarioId(catalogoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        Produto produto = buscarProduto(request.getProdutoId(), usuarioId);
        validarProdutoTemCusto(produto); // RN-044

        List<ItemCatalogoCustomizacao> customizacoes = new ArrayList<>();
        BigDecimal precoVendaCustomizacoes = BigDecimal.ZERO;
        if (request.getCustomizacoesAnexadas() != null) {
            for (CustomizacaoAnexadaRequest req : request.getCustomizacoesAnexadas()) {
                Produto produtoCustomizacao = buscarProduto(req.getProdutoId(), usuarioId);
                validarTipoCustomizacao(produtoCustomizacao);
                customizacoes.add(ItemCatalogoCustomizacao.builder()
                        .produto(produtoCustomizacao)
                        .quantidade(req.getQuantidade())
                        .build());
                precoVendaCustomizacoes = precoVendaCustomizacoes.add(
                        produtoCustomizacao.getPrecoVenda().multiply(req.getQuantidade()));
            }
        }

        BigDecimal precoSugerido = calcularPrecoSugerido(produto, request.getQuantidadePacote(), customizacoes);
        return new ItemCatalogoPrecoSugeridoResponse(
                produto.getPrecoVenda(), request.getQuantidadePacote(), precoVendaCustomizacoes, precoSugerido);
    }

    // ---------------------------------------------------------------
    // Escrita
    // ---------------------------------------------------------------

    public ItemCatalogoResponse adicionar(UUID catalogoId, ItemCatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Catalogo catalogo = catalogoRepository.findByIdAndUsuarioId(catalogoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        Produto produto = buscarProduto(request.getProdutoId(), usuarioId);
        validarProdutoTemCusto(produto); // RN-044

        ItemCatalogo item = itemCatalogoMapper.toEntity(request, catalogo, produto);
        item.setPrecoVenda(BigDecimal.ZERO); // placeholder — preco_venda é NOT NULL; sobrescrito por aplicarPrecoVenda antes do commit
        item = itemCatalogoRepository.save(item); // precisa de id para anexar customizações

        List<ItemCatalogoCustomizacao> customizacoes = salvarCustomizacoes(item, request.getCustomizacoesAnexadas(), usuarioId);

        BigDecimal precoSugerido = calcularPrecoSugerido(produto, request.getQuantidadePacote(), customizacoes);
        aplicarPrecoVenda(item, request.getPrecoVenda(), precoSugerido); // CAT-003 / RN-038a
        item = itemCatalogoRepository.save(item);

        return montarResponse(item, customizacoes, precoSugerido);
    }

    public ItemCatalogoResponse editar(UUID itemId, ItemCatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        ItemCatalogo item = buscarItemDoUsuario(itemId, usuarioId);

        Produto produto = buscarProduto(request.getProdutoId(), usuarioId);
        validarProdutoTemCusto(produto); // RN-044

        item.setProduto(produto);
        item.setQuantidadePacote(request.getQuantidadePacote());

        // customizações são recriadas do zero (não têm soft delete próprio)
        customizacaoRepository.deleteAll(customizacaoRepository.findByItemCatalogoId(item.getId()));
        List<ItemCatalogoCustomizacao> customizacoes = salvarCustomizacoes(item, request.getCustomizacoesAnexadas(), usuarioId);

        BigDecimal precoSugerido = calcularPrecoSugerido(produto, request.getQuantidadePacote(), customizacoes);
        aplicarPrecoVenda(item, request.getPrecoVenda(), precoSugerido); // RN-038a
        item = itemCatalogoRepository.save(item);

        return montarResponse(item, customizacoes, precoSugerido);
    }

    public void remover(UUID itemId) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        item.setDeletedAt(LocalDateTime.now());
        itemCatalogoRepository.save(item);
    }

    // ---------------------------------------------------------------
    // CAT-003 — cálculo do preço sugerido: herda o preço de venda do produto e das customizações
    // anexadas, sem margem própria de Catálogo (#239)
    // ---------------------------------------------------------------

    public BigDecimal calcularPrecoSugerido(Produto produto, Integer quantidadePacote,
                                            List<ItemCatalogoCustomizacao> customizacoes) {
        BigDecimal precoVendaBase = produto.getPrecoVenda().multiply(BigDecimal.valueOf(quantidadePacote));
        BigDecimal precoVendaCustomizacoes = BigDecimal.ZERO;
        for (ItemCatalogoCustomizacao customizacao : customizacoes) {
            precoVendaCustomizacoes = precoVendaCustomizacoes.add(
                    customizacao.getProduto().getPrecoVenda().multiply(customizacao.getQuantidade()));
        }
        return precoVendaBase.add(precoVendaCustomizacoes).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** RN-038a — override quando o preço informado diverge do sugerido; senão acompanha o preço de venda do produto. */
    private void aplicarPrecoVenda(ItemCatalogo item, BigDecimal precoVendaInformado, BigDecimal precoSugerido) {
        if (precoVendaInformado != null && precoVendaInformado.compareTo(precoSugerido) != 0) {
            item.setPrecoVenda(precoVendaInformado);
            item.setOverride(true);
        } else {
            item.setPrecoVenda(precoSugerido);
            item.setOverride(false);
        }
    }

    private List<ItemCatalogoCustomizacao> salvarCustomizacoes(ItemCatalogo item,
                                                               List<CustomizacaoAnexadaRequest> requests, UUID usuarioId) {
        List<ItemCatalogoCustomizacao> salvas = new ArrayList<>();
        if (requests == null) {
            return salvas;
        }
        for (CustomizacaoAnexadaRequest req : requests) {
            Produto produtoCustomizacao = buscarProduto(req.getProdutoId(), usuarioId);
            validarTipoCustomizacao(produtoCustomizacao);
            salvas.add(customizacaoRepository.save(
                    itemCatalogoMapper.toCustomizacaoEntity(req, item, produtoCustomizacao)));
        }
        return salvas;
    }

    private void validarTipoCustomizacao(Produto produto) {
        if (produto.getTipo() != TipoProduto.CUSTOMIZACAO) {
            throw new BusinessException("O produto anexado como customização deve ser do tipo Customização.");
        }
    }

    private ItemCatalogoResponse montarResponse(ItemCatalogo item) {
        List<ItemCatalogoCustomizacao> customizacoes = customizacaoRepository.findByItemCatalogoId(item.getId());
        BigDecimal precoSugerido = calcularPrecoSugerido(item.getProduto(), item.getQuantidadePacote(), customizacoes);
        return montarResponse(item, customizacoes, precoSugerido);
    }

    private ItemCatalogoResponse montarResponse(ItemCatalogo item, List<ItemCatalogoCustomizacao> customizacoes,
                                                BigDecimal precoSugerido) {
        List<FichaTecnicaItem> fichaTecnicaProduto = fichaTecnicaItemRepository.findByProdutoId(item.getProduto().getId());
        ItemCatalogoResponse response = itemCatalogoMapper.toResponse(item, customizacoes, fichaTecnicaProduto);
        response.setPrecoSugerido(precoSugerido);
        response.setBloqueadoParaVenda(produtoBloqueado(item.getProduto())); // RN-045
        return response;
    }

    private boolean produtoBloqueado(Produto produto) {
        return !Boolean.TRUE.equals(produto.getAtivo()) || produto.getDeletedAt() != null;
    }

    private void validarProdutoTemCusto(Produto produto) {
        if (produto.getPrecoCusto() == null || produto.getPrecoCusto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "O produto não possui custo calculado. Complete o cadastro do produto (ficha técnica e rendimento) antes de adicioná-lo ao catálogo.");
        }
    }

    private Produto buscarProduto(UUID produtoId, UUID usuarioId) {
        return produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
    }

    private ItemCatalogo buscarItemDoUsuario(UUID itemId, UUID usuarioId) {
        ItemCatalogo item = itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item do catálogo não encontrado"));
        if (!item.getCatalogo().getUsuario().getId().equals(usuarioId)) {
            throw new ResourceNotFoundException("Item do catálogo não encontrado");
        }
        return item;
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
        return usuario.getId();
    }
}
