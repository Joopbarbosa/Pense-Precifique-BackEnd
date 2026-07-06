package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.Catalogo;
import com.penseprecifique.api.domain.entity.ItemCatalogo;
import com.penseprecifique.api.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.TipoProduto;
import com.penseprecifique.api.dto.request.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.dto.request.ItemCatalogoRequest;
import com.penseprecifique.api.dto.response.ItemCatalogoResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.mapper.ItemCatalogoMapper;
import com.penseprecifique.api.repository.CatalogoRepository;
import com.penseprecifique.api.repository.ItemCatalogoCustomizacaoRepository;
import com.penseprecifique.api.repository.ItemCatalogoRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
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

    private static final BigDecimal CEM = new BigDecimal("100");

    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoCustomizacaoRepository customizacaoRepository;
    private final CatalogoRepository catalogoRepository;
    private final ProdutoRepository produtoRepository;
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

    @Transactional(readOnly = true)
    public ItemCatalogoResponse buscarPorId(UUID itemId) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        return montarResponse(item);
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

        BigDecimal precoSugerido = calcularPrecoSugerido(produto, request.getQuantidadePacote(), customizacoes, catalogo.getMargem());
        aplicarPrecoVenda(item, request.getPrecoVenda(), precoSugerido); // RN-042 / RN-038a
        item = itemCatalogoRepository.save(item);

        return montarResponse(item, customizacoes, precoSugerido);
    }

    public ItemCatalogoResponse editar(UUID itemId, ItemCatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        ItemCatalogo item = buscarItemDoUsuario(itemId, usuarioId);
        Catalogo catalogo = item.getCatalogo();

        Produto produto = buscarProduto(request.getProdutoId(), usuarioId);
        validarProdutoTemCusto(produto); // RN-044

        item.setProduto(produto);
        item.setQuantidadePacote(request.getQuantidadePacote());

        // customizações são recriadas do zero (não têm soft delete próprio)
        customizacaoRepository.deleteAll(customizacaoRepository.findByItemCatalogoId(item.getId()));
        List<ItemCatalogoCustomizacao> customizacoes = salvarCustomizacoes(item, request.getCustomizacoesAnexadas(), usuarioId);

        BigDecimal precoSugerido = calcularPrecoSugerido(produto, request.getQuantidadePacote(), customizacoes, catalogo.getMargem());
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
    // RN-042 — cálculo do preço sugerido (reutilizado no recálculo por margem do Catálogo)
    // ---------------------------------------------------------------

    public BigDecimal calcularPrecoSugerido(Produto produto, Integer quantidadePacote,
                                            List<ItemCatalogoCustomizacao> customizacoes, BigDecimal margem) {
        BigDecimal custoBase = produto.getPrecoCusto().multiply(BigDecimal.valueOf(quantidadePacote));
        BigDecimal custoCustomizacoes = BigDecimal.ZERO;
        for (ItemCatalogoCustomizacao customizacao : customizacoes) {
            custoCustomizacoes = custoCustomizacoes.add(
                    customizacao.getProduto().getPrecoCusto().multiply(customizacao.getQuantidade()));
        }
        BigDecimal margemAplicada = margem != null ? margem : BigDecimal.ZERO;
        BigDecimal fator = BigDecimal.ONE.add(margemAplicada.divide(CEM, 6, RoundingMode.HALF_UP));
        return custoBase.add(custoCustomizacoes).multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * RN-042 — recalcula o preço de venda de um item quando a MARGEM do catálogo muda.
     * Só deve ser chamado para itens sem override (a decisão fica no {@code CatalogoService}).
     */
    public void recalcularPrecoVendaPorMargem(ItemCatalogo item, BigDecimal novaMargem) {
        List<ItemCatalogoCustomizacao> customizacoes = customizacaoRepository.findByItemCatalogoId(item.getId());
        BigDecimal precoSugerido = calcularPrecoSugerido(item.getProduto(), item.getQuantidadePacote(), customizacoes, novaMargem);
        item.setPrecoVenda(precoSugerido);
        item.setOverride(false);
        itemCatalogoRepository.save(item);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** RN-038a — override quando o preço informado diverge do sugerido; senão acompanha a margem. */
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
            if (produtoCustomizacao.getTipo() != TipoProduto.CUSTOMIZACAO) {
                throw new BusinessException("O produto anexado como customização deve ser do tipo Customização.");
            }
            salvas.add(customizacaoRepository.save(
                    itemCatalogoMapper.toCustomizacaoEntity(req, item, produtoCustomizacao)));
        }
        return salvas;
    }

    private ItemCatalogoResponse montarResponse(ItemCatalogo item) {
        List<ItemCatalogoCustomizacao> customizacoes = customizacaoRepository.findByItemCatalogoId(item.getId());
        BigDecimal precoSugerido = calcularPrecoSugerido(item.getProduto(), item.getQuantidadePacote(),
                customizacoes, item.getCatalogo().getMargem());
        return montarResponse(item, customizacoes, precoSugerido);
    }

    private ItemCatalogoResponse montarResponse(ItemCatalogo item, List<ItemCatalogoCustomizacao> customizacoes,
                                                BigDecimal precoSugerido) {
        ItemCatalogoResponse response = itemCatalogoMapper.toResponse(item, customizacoes);
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
