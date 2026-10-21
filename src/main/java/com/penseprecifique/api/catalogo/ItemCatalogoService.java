package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.empresa.ConfiguracaoPrecificacaoRepository;
import com.penseprecifique.api.infra.storage.R2StorageClient;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.ConfiguracaoPrecificacao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoPreviewRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoPrecoSugeridoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ItemCatalogoMapper;
import com.penseprecifique.api.shared.validation.ValidadorArquivoImagem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import com.penseprecifique.api.produto.CausaPrecoSugerido;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * RN-NOVA-1/2/3 (V0.13.0, #516) — Item de Catálogo passa de "1 produto + N customizações anexadas"
 * (CAT-003, removido) para uma composição livre de N componentes (Produto/Customização OU Insumo,
 * mesmo formato de {@code FichaTecnicaService}), com custo/margem/preço próprios (mesmo modelo
 * calculado+override de Produto, PDT-005/PDT-006).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ItemCatalogoService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal SESSENTA = new BigDecimal("60");

    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoComponenteRepository componenteRepository;
    private final CatalogoRepository catalogoRepository;
    private final ProdutoRepository produtoRepository;
    private final InsumoRepository insumoRepository;
    private final ConfiguracaoPrecificacaoRepository configuracaoPrecificacaoRepository;
    private final ItemCatalogoMapper itemCatalogoMapper;
    private final UsuarioRepository usuarioRepository;
    private final R2StorageClient r2StorageClient;
    private final ValidadorArquivoImagem validadorArquivoImagem;
    private final com.penseprecifique.api.produto.PrecoSugeridoHistoricoService precoSugeridoHistoricoService;
    private final com.penseprecifique.api.produto.MudancaPrecoSugeridoRepository mudancaPrecoSugeridoRepository;

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
     * RN-044/045/046 — busca de itens de catálogo para a Seção Itens do orçamento. RN-NOVA-4 —
     * item com qualquer componente inativo não aparece (filtro na query do Repository).
     */
    @Transactional(readOnly = true)
    public Page<ItemCatalogoBuscaResponse> buscarParaOrcamento(UUID catalogoId, String busca, Pageable pageable) {
        return buscarParaOrcamento(catalogoId, busca, false, pageable);
    }

    /**
     * #641/RN-NOVA-40 (V0.15.0) — {@code incluirInativos}: traz também os itens indisponíveis
     * (catálogo inativo ou componente inativo/excluído), depois dos disponíveis, com {@code ativo=false}.
     */
    @Transactional(readOnly = true)
    public Page<ItemCatalogoBuscaResponse> buscarParaOrcamento(UUID catalogoId, String busca, boolean incluirInativos,
                                                              Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        boolean temBusca = busca != null && !busca.isBlank();
        Page<ItemCatalogo> itens = temBusca
                ? itemCatalogoRepository.buscarDisponiveisParaOrcamentoComBusca(usuarioId, catalogoId, busca.trim(), incluirInativos, pageable)
                : itemCatalogoRepository.buscarDisponiveisParaOrcamento(usuarioId, catalogoId, incluirInativos, pageable);
        return itens.map(item -> itemCatalogoMapper.toBuscaResponse(item, componenteRepository.findByItemCatalogoId(item.getId())));
    }

    /**
     * CAT-013 — preview ao vivo do custo/preço sugerido (produto/customização/insumo + quantidade +
     * tempo de produção + margem), sem persistir nada.
     */
    @Transactional(readOnly = true)
    public ItemCatalogoPrecoSugeridoResponse previewPreco(UUID catalogoId, ItemCatalogoPreviewRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        catalogoRepository.findByIdAndUsuarioId(catalogoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        BigDecimal custoComponentes = BigDecimal.ZERO;
        for (ItemCatalogoComponenteRequest req : request.getComponentes()) {
            exigirUmaReferencia(req);
            BigDecimal custoUnitario = resolverCustoUnitarioComponente(req, usuarioId);
            custoComponentes = custoComponentes.add(req.getQuantidade().multiply(custoUnitario));
        }
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoMaoDeObra = calcularCustoMaoDeObra(request.getTempoProducao(), valorHora);
        BigDecimal custoTotal = custoComponentes.add(custoMaoDeObra);
        BigDecimal precoSugerido = calcularPrecoSugerido(custoTotal, request.getMargemLucro());

        return new ItemCatalogoPrecoSugeridoResponse(custoComponentes, custoMaoDeObra, custoTotal, precoSugerido);
    }

    // ---------------------------------------------------------------
    // Escrita
    // ---------------------------------------------------------------

    /** #1017 (RN-NOVA-36) — nome + marca únicos entre os itens não excluídos do mesmo catálogo, sem maiúsculas, acentos e espaços das pontas. */
    private void validarNomeMarcaUnicos(UUID catalogoId, UUID ignorarId, String nome, String marca, String campo) {
        for (Object[] linha : itemCatalogoRepository.identidadesDoCatalogo(catalogoId)) {
            if (linha[0].equals(ignorarId)) continue;
            if (com.penseprecifique.api.util.NomeMarca.mesmaIdentidade(nome, marca, (String) linha[1], (String) linha[2])) {
                throw BusinessException.noCampo(campo, "Já existe um item neste catálogo com este nome e esta marca");
            }
        }
    }

    public ItemCatalogoResponse adicionar(UUID catalogoId, ItemCatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Catalogo catalogo = catalogoRepository.findByIdAndUsuarioId(catalogoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        usuarioRepository.lockPorId(usuarioId);
        validarNomeMarcaUnicos(catalogoId, null, request.getNome(), com.penseprecifique.api.util.NomeMarca.limparMarca(request.getMarca()), "nome");
        ItemCatalogo item = itemCatalogoMapper.toEntity(request, catalogo);
        if (item.getMargemLucro() == null) {
            item.setMargemLucro(buscarMargemPadrao(usuarioId));
        }
        item.setPrecoVenda(BigDecimal.ZERO); // placeholder — preco_venda é NOT NULL; sobrescrito antes do commit
        item = itemCatalogoRepository.save(item); // precisa de id para gravar os componentes

        List<ItemCatalogoComponente> componentes = salvarComponentes(item, request.getComponentes(), usuarioId);

        BigDecimal custoTotal = calcularCustoTotal(componentes, item.getTempoProducao(), usuarioId);
        BigDecimal precoSugerido = calcularPrecoSugerido(custoTotal, item.getMargemLucro());
        aplicarPrecoVendaCriacao(item, request.getPrecoVenda(), precoSugerido);
        item = itemCatalogoRepository.save(item);

        return montarResponse(item, componentes, custoTotal, precoSugerido);
    }

    public ItemCatalogoResponse editar(UUID itemId, ItemCatalogoRequest request) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        UUID usuarioId = getUsuarioIdAutenticado();

        BigDecimal precoVendaAntigo = item.getPrecoVenda();
        BigDecimal margemLucroAntigo = item.getMargemLucro();
        // #961/RN-NOVA-28 — preço sugerido de antes (ao vivo), capturado antes de qualquer alteração da edição
        BigDecimal custoTotalAntes = calcularCustoTotal(componenteRepository.findByItemCatalogoId(item.getId()), item.getTempoProducao(), usuarioId);
        BigDecimal precoSugeridoAntes = calcularPrecoSugerido(custoTotalAntes, margemLucroAntigo);

        String marcaNova = com.penseprecifique.api.util.NomeMarca.limparMarca(request.getMarca());
        boolean nomeMudou = !com.penseprecifique.api.util.NomeMarca.chave(request.getNome()).equals(com.penseprecifique.api.util.NomeMarca.chave(item.getNome()));
        boolean marcaMudou = !com.penseprecifique.api.util.NomeMarca.chave(marcaNova).equals(com.penseprecifique.api.util.NomeMarca.chave(item.getMarca()));
        if (nomeMudou || marcaMudou) {
            // #1017 — só quando nome ou marca mudam; item antigo repetido segue editável nos outros campos
            usuarioRepository.lockPorId(usuarioId);
            validarNomeMarcaUnicos(item.getCatalogo().getId(), item.getId(), request.getNome(), marcaNova, nomeMudou ? "nome" : "marca");
        }
        item.setNome(request.getNome());
        item.setMarca(marcaNova);
        item.setTempoProducao(request.getTempoProducao());
        item.setMargemLucro(request.getMargemLucro() != null ? request.getMargemLucro() : margemLucroAntigo);
        item.setDescricao(request.getDescricao());

        // componentes são recriados do zero (não têm soft delete próprio, mesmo padrão já usado
        // pelas antigas customizações anexadas)
        componenteRepository.deleteByItemCatalogoId(item.getId());
        List<ItemCatalogoComponente> componentes = salvarComponentes(item, request.getComponentes(), usuarioId);

        BigDecimal custoTotal = calcularCustoTotal(componentes, item.getTempoProducao(), usuarioId);
        BigDecimal precoSugerido = calcularPrecoSugerido(custoTotal, item.getMargemLucro());
        aplicarPrecoVendaEdicao(item, request.getPrecoVenda(), precoSugerido, precoVendaAntigo);
        item = itemCatalogoRepository.save(item);

        // #961 — a edição do próprio item entra no histórico; é "mudança de margem" quando só a margem mexeu no preço
        boolean soMargem = margemLucroAntigo != null && item.getMargemLucro() != null
                && margemLucroAntigo.compareTo(item.getMargemLucro()) != 0 && custoTotalAntes.compareTo(custoTotal) == 0;
        precoSugeridoHistoricoService.registrarItemSeMudou(usuarioId, item, precoSugeridoAntes, precoSugerido,
                soMargem ? CausaPrecoSugerido.ITEM_MARGEM : CausaPrecoSugerido.ITEM_EDICAO, item.getId(), null, java.util.List.of());

        return montarResponse(item, componentes, custoTotal, precoSugerido);
    }

    public void remover(UUID itemId) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        item.setDeletedAt(LocalDateTime.now());
        itemCatalogoRepository.save(item);
        // #518 — remover o item não pode deixar o objeto órfão no R2 (custo de armazenamento sem
        // nenhuma referência viva); melhor esforço, mesma semântica de uploadFoto/removerFoto.
        if (item.getFotoUrl() != null) {
            r2StorageClient.deletarPorUrl(item.getFotoUrl());
        }
    }

    /**
     * RN-NOVA-6/DT-NOVA-4 — valida formato/tamanho ANTES de subir pro R2 (nunca confia só na
     * validação do Frontend). Substitui a foto anterior, se houver — remoção do objeto antigo é
     * melhor esforço (não falha a troca se o objeto antigo já não existir mais no bucket).
     */
    public ItemCatalogoResponse uploadFoto(UUID itemId, MultipartFile arquivo) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        validadorArquivoImagem.validar(arquivo);

        String extensao = validadorArquivoImagem.extensaoPara(arquivo);
        String key = "catalogo/item-catalogo/" + item.getId() + "/" + UUID.randomUUID() + "." + extensao;

        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o arquivo enviado. Tente novamente.");
        }

        String fotoUrlAntiga = item.getFotoUrl();
        String novaUrl = r2StorageClient.upload(key, conteudo, arquivo.getContentType());
        item.setFotoUrl(novaUrl);
        item = itemCatalogoRepository.save(item);
        if (fotoUrlAntiga != null) {
            r2StorageClient.deletarPorUrl(fotoUrlAntiga);
        }

        return montarResponse(item);
    }

    public ItemCatalogoResponse removerFoto(UUID itemId) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        if (item.getFotoUrl() != null) {
            r2StorageClient.deletarPorUrl(item.getFotoUrl());
            item.setFotoUrl(null);
            item = itemCatalogoRepository.save(item);
        }
        return montarResponse(item);
    }

    // ---------------------------------------------------------------
    // RN-NOVA-2 — custo total do item: soma dos componentes + mão de obra própria
    // ---------------------------------------------------------------

    private BigDecimal calcularCustoTotal(List<ItemCatalogoComponente> componentes, Integer tempoProducao, UUID usuarioId) {
        BigDecimal custoComponentes = BigDecimal.ZERO;
        for (ItemCatalogoComponente componente : componentes) {
            BigDecimal custoUnitario = componente.getInsumo() != null
                    ? componente.getInsumo().getCustoUnitario()
                    : componente.getProdutoBase().getPrecoCusto();
            custoComponentes = custoComponentes.add(componente.getQuantidade().multiply(custoUnitario));
        }
        BigDecimal valorHora = buscarValorHora(usuarioId);
        return custoComponentes.add(calcularCustoMaoDeObra(tempoProducao, valorHora));
    }

    private BigDecimal calcularCustoMaoDeObra(Integer tempoProducaoMinutos, BigDecimal valorHora) {
        return BigDecimal.valueOf(tempoProducaoMinutos)
                .divide(SESSENTA, 6, RoundingMode.HALF_UP)
                .multiply(valorHora);
    }

    /** RN-NOVA-3 — mesma fórmula multiplicativa de Produto (PDT-005): custoTotal × (1 + margem/100). */
    private BigDecimal calcularPrecoSugerido(BigDecimal custoTotal, BigDecimal margemLucro) {
        BigDecimal margem = margemLucro != null ? margemLucro : BigDecimal.ZERO;
        BigDecimal fator = BigDecimal.ONE.add(margem.divide(CEM, 6, RoundingMode.HALF_UP));
        return custoTotal.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal buscarValorHora(UUID usuarioId) {
        return configuracaoPrecificacaoRepository.findByUsuarioId(usuarioId)
                .map(ConfiguracaoPrecificacao::getValorHora)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal buscarMargemPadrao(UUID usuarioId) {
        return configuracaoPrecificacaoRepository.findByUsuarioId(usuarioId)
                .map(ConfiguracaoPrecificacao::getMargemPadrao)
                .orElse(BigDecimal.ZERO);
    }

    /** Mesmo modelo calculado+override de Produto (aplicarPrecoVendaCriacao). */
    private void aplicarPrecoVendaCriacao(ItemCatalogo item, BigDecimal precoVendaInformado, BigDecimal precoSugerido) {
        if (precoVendaInformado != null && precoVendaInformado.compareTo(precoSugerido) != 0) {
            item.setPrecoVenda(precoVendaInformado);
            item.setOverride(true);
        } else {
            item.setPrecoVenda(precoSugerido);
            item.setOverride(false);
        }
    }

    /** #579/RN-NOVA-30 — mesma regra de Produto: preço final só muda quando a artesã digita outro valor. */
    private void aplicarPrecoVendaEdicao(ItemCatalogo item, BigDecimal precoVendaInformado, BigDecimal precoSugerido,
                                          BigDecimal precoVendaAntigo) {
        BigDecimal precoFinal = precoVendaInformado != null && precoVendaInformado.compareTo(precoVendaAntigo) != 0
                ? precoVendaInformado
                : precoVendaAntigo;
        item.setPrecoVenda(precoFinal);
        item.setOverride(precoFinal.compareTo(precoSugerido) != 0);
    }

    // ---------------------------------------------------------------
    // Helpers — componentes
    // ---------------------------------------------------------------

    /** #1029 — a prévia de preço recusa o componente sem referência (ou com as duas) com a mesma mensagem do cadastro do item. */
    private static void exigirUmaReferencia(ItemCatalogoComponenteRequest req) {
        if ((req.getInsumoId() != null) == (req.getProdutoBaseId() != null)) {
            throw new BusinessException("Cada componente do item de catálogo deve referenciar exatamente um insumo ou um produto.");
        }
    }

    private List<ItemCatalogoComponente> salvarComponentes(ItemCatalogo item, List<ItemCatalogoComponenteRequest> requests, UUID usuarioId) {
        return requests.stream().map(req -> {
            exigirUmaReferencia(req);
            boolean temInsumo = req.getInsumoId() != null;

            Insumo insumo = null;
            Produto produtoBase = null;
            if (temInsumo) {
                insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getInsumoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + req.getInsumoId()));
                if (!Boolean.TRUE.equals(insumo.getAtivo())) {
                    throw new BusinessException("Este insumo está inativo e não pode ser adicionado. Reative-o para continuar.");
                }
                validarQuantidadeInsumo(insumo, req.getQuantidade());
            } else {
                produtoBase = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getProdutoBaseId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + req.getProdutoBaseId()));
                if (!Boolean.TRUE.equals(produtoBase.getAtivo())) {
                    throw new BusinessException("Apenas produtos/customizações ativos podem ser usados como componente de item de catálogo.");
                }
                validarProdutoTemCusto(produtoBase);
            }

            return componenteRepository.save(itemCatalogoMapper.toComponenteEntity(req, item, insumo, produtoBase));
        }).toList();
    }

    /** Mesma regra de FichaTecnicaService#validarQuantidadeInsumo — duplicada aqui deliberadamente
     * (módulo diferente, evita acoplar Catálogo a Produto só por este helper de 6 linhas). */
    private void validarQuantidadeInsumo(Insumo insumo, BigDecimal quantidade) {
        if (!insumo.getFracionavel()) {
            if (quantidade.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(
                        "O insumo '" + insumo.getNome() + "' não pode ser usado em fração — informe uma quantidade inteira.");
            }
        } else if (quantidade.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Quantidade aceita no máximo 2 casas decimais.");
        }
    }

    /** RN-044 — mesma validação já usada antes de CAT-003 (produto sem custo calculado não pode
     * virar componente de item de catálogo). */
    private void validarProdutoTemCusto(Produto produto) {
        if (produto.getPrecoCusto() == null || produto.getPrecoCusto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "O produto não possui custo calculado. Complete o cadastro do produto (ficha técnica e rendimento) antes de adicioná-lo ao catálogo.");
        }
    }

    private BigDecimal resolverCustoUnitarioComponente(ItemCatalogoComponenteRequest req, UUID usuarioId) {
        if (req.getInsumoId() != null) {
            return insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getInsumoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + req.getInsumoId()))
                    .getCustoUnitario();
        }
        return produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getProdutoBaseId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + req.getProdutoBaseId()))
                .getPrecoCusto();
    }

    // ---------------------------------------------------------------
    // Helpers — resposta
    // ---------------------------------------------------------------

    private ItemCatalogoResponse montarResponse(ItemCatalogo item) {
        List<ItemCatalogoComponente> componentes = componenteRepository.findByItemCatalogoId(item.getId());
        BigDecimal custoTotal = calcularCustoTotal(componentes, item.getTempoProducao(), getUsuarioIdDoItem(item));
        BigDecimal precoSugerido = calcularPrecoSugerido(custoTotal, item.getMargemLucro());
        return montarResponse(item, componentes, custoTotal, precoSugerido);
    }

    private ItemCatalogoResponse montarResponse(ItemCatalogo item, List<ItemCatalogoComponente> componentes,
                                                 BigDecimal custoTotal, BigDecimal precoSugerido) {
        ItemCatalogoResponse response = itemCatalogoMapper.toResponse(item, componentes);
        response.setCustoTotal(custoTotal);
        response.setPrecoSugerido(precoSugerido);
        response.setBloqueadoParaVenda(bloqueadoParaVenda(componentes)); // RN-NOVA-4
        return response;
    }

    /** RN-NOVA-4 — qualquer componente inativo/excluído bloqueia a venda do item inteiro. */
    private boolean bloqueadoParaVenda(List<ItemCatalogoComponente> componentes) {
        return componentes.stream().anyMatch(c -> {
            if (c.getInsumo() != null) {
                return !Boolean.TRUE.equals(c.getInsumo().getAtivo()) || c.getInsumo().getDeletedAt() != null;
            }
            Produto p = c.getProdutoBase();
            return !Boolean.TRUE.equals(p.getAtivo()) || p.getDeletedAt() != null;
        });
    }

    // ---------------------------------------------------------------
    // #961/RN-NOVA-28 — histórico do preço sugerido do item (mesmo mecanismo do Produto, DT-NOVA-13)
    // ---------------------------------------------------------------

    /** Preço sugerido ao vivo do item (custo dos componentes ao vivo e tempo de produção × margem do item). */
    @Transactional(readOnly = true)
    public BigDecimal precoSugeridoAoVivo(ItemCatalogo item) {
        List<ItemCatalogoComponente> componentes = componenteRepository.findByItemCatalogoId(item.getId());
        return calcularPrecoSugerido(calcularCustoTotal(componentes, item.getTempoProducao(), getUsuarioIdDoItem(item)), item.getMargemLucro());
    }

    /**
     * Preço sugerido do item com custos substitutos (insumo e produto/customização componentes) — o "antes" de um evento de
     * compra, calculado com os custos de antes enquanto os de agora já estão gravados.
     */
    @Transactional(readOnly = true)
    public BigDecimal precoSugeridoSimulado(UUID itemId, Map<UUID, BigDecimal> custoInsumo, Map<UUID, BigDecimal> custoProduto) {
        return itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemId).map(item -> {
            BigDecimal componentes = BigDecimal.ZERO;
            for (ItemCatalogoComponente c : componenteRepository.findByItemCatalogoId(item.getId())) {
                BigDecimal unitario = c.getInsumo() != null
                        ? custoInsumo.getOrDefault(c.getInsumo().getId(), c.getInsumo().getCustoUnitario())
                        : custoProduto.getOrDefault(c.getProdutoBase().getId(), c.getProdutoBase().getPrecoCusto());
                componentes = componentes.add(c.getQuantidade().multiply(unitario));
            }
            BigDecimal custoTotal = componentes.add(calcularCustoMaoDeObra(item.getTempoProducao(), buscarValorHora(getUsuarioIdDoItem(item))));
            return calcularPrecoSugerido(custoTotal, item.getMargemLucro());
        }).orElse(null);
    }

    /** Preço sugerido (ao vivo) de cada item informado; ignora os excluídos. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> capturarPrecosSugeridos(java.util.Collection<UUID> itemIds) {
        Map<UUID, BigDecimal> precos = new java.util.LinkedHashMap<>();
        for (UUID id : itemIds) {
            itemCatalogoRepository.findByIdAndDeletedAtIsNull(id).ifPresent(i -> precos.put(id, precoSugeridoAoVivo(i)));
        }
        return precos;
    }

    /** Depois do evento, compara com o preço sugerido ao vivo de agora e grava só quem mudou em centavos. */
    public void registrarMudancasPrecoSugerido(UUID usuarioId, Map<UUID, BigDecimal> antes, CausaPrecoSugerido causa,
                                               UUID referenciaId, String referenciaTexto,
                                               Map<UUID, ? extends java.util.Collection<UUID>> insumosPorItem) {
        for (Map.Entry<UUID, BigDecimal> e : antes.entrySet()) {
            java.util.Collection<UUID> origem = insumosPorItem.get(e.getKey());
            itemCatalogoRepository.findByIdAndDeletedAtIsNull(e.getKey()).ifPresent(i ->
                    precoSugeridoHistoricoService.registrarItemSeMudou(usuarioId, i, e.getValue(), precoSugeridoAoVivo(i), causa,
                            referenciaId, referenciaTexto, origem != null ? origem : List.<UUID>of()));
        }
    }

    /** Itens que têm algum dos insumos como componente direto. */
    @Transactional(readOnly = true)
    public Set<UUID> itensComInsumos(java.util.Collection<UUID> insumoIds) {
        return insumoIds.isEmpty() ? Set.of() : new java.util.LinkedHashSet<>(componenteRepository.findItemIdsByInsumoIds(insumoIds));
    }

    /** Itens que têm algum dos produtos ou customizações como componente. */
    @Transactional(readOnly = true)
    public Set<UUID> itensComProdutos(java.util.Collection<UUID> produtoIds) {
        return produtoIds.isEmpty() ? Set.of() : new java.util.LinkedHashSet<>(componenteRepository.findItemIdsByProdutoBaseIds(produtoIds));
    }

    /** Todos os itens da conta (o valor da hora alcança todos). */
    @Transactional(readOnly = true)
    public Set<UUID> idsDosItensDaConta(UUID usuarioId) {
        return new java.util.LinkedHashSet<>(itemCatalogoRepository.idsDaConta(usuarioId));
    }

    /** Histórico do item, da mudança mais recente para a mais antiga, paginado (RN-086). Item de outra conta: não encontrado. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.penseprecifique.api.shared.dto.response.catalogo.HistoricoPrecoItemResponse> historicoPreco(
            UUID catalogoId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
        ItemCatalogo item = buscarItemDoUsuario(itemId, getUsuarioIdAutenticado());
        if (!item.getCatalogo().getId().equals(catalogoId)) {
            throw new ResourceNotFoundException("Item do catálogo não encontrado");
        }
        int tamanho = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        return mudancaPrecoSugeridoRepository.findDoItem(item.getId(),
                        org.springframework.data.domain.PageRequest.of(Math.max(pageable.getPageNumber(), 0), tamanho))
                .map(m -> new com.penseprecifique.api.shared.dto.response.catalogo.HistoricoPrecoItemResponse(
                        m.getId(), m.getValorAnterior(), m.getValorNovo(), m.getCausa().name(), m.getReferenciaTexto(),
                        m.getReferenciaId(), m.getCreatedAt()));
    }

    /**
     * Usado por ProdutoService após substituir o produto/customização de um componente na resolução
     * de vínculo: recalcula só o preço sugerido. #579/RN-NOVA-30 (V0.15.0) — o preço final nunca muda
     * aqui (antes seguia o sugerido quando o item não estava em override, sem a artesã aprovar).
     */
    public void recalcularAposSubstituicaoComponente(UUID itemCatalogoId) {
        ItemCatalogo item = itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemCatalogoId)
                .orElseThrow(() -> new ResourceNotFoundException("Item do catálogo não encontrado"));
        List<ItemCatalogoComponente> componentes = componenteRepository.findByItemCatalogoId(item.getId());
        BigDecimal custoTotal = calcularCustoTotal(componentes, item.getTempoProducao(), getUsuarioIdDoItem(item));
        BigDecimal precoSugerido = calcularPrecoSugerido(custoTotal, item.getMargemLucro());
        item.setOverride(item.getPrecoVenda().compareTo(precoSugerido) != 0);
        itemCatalogoRepository.save(item);
    }

    private UUID getUsuarioIdDoItem(ItemCatalogo item) {
        return item.getCatalogo().getUsuario().getId();
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
