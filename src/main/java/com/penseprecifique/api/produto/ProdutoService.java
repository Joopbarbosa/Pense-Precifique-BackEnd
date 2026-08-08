package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.ConfiguracaoPrecificacao;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoVinculoProduto;
import com.penseprecifique.api.shared.dto.request.produto.BaixaManualProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ResolucaoVinculoCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ResolucaoVinculoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.produto.ResolverVinculosProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.SubstituicaoComponenteVinculoRequest;
import com.penseprecifique.api.shared.dto.request.produto.SubstituicaoVinculoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.CatalogoVinculadoResponse;
import com.penseprecifique.api.shared.dto.response.produto.ComponenteVinculadoResponse;
import com.penseprecifique.api.shared.dto.response.produto.MovimentacaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.produto.PrecoSugeridoResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoContagensResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ProdutoMapper;
import com.penseprecifique.api.catalogo.ItemCatalogoCustomizacaoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.empresa.ConfiguracaoPrecificacaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.util.NumeroSequencialUtil;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProdutoService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal SESSENTA = new BigDecimal("60");

    private final ProdutoRepository produtoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final FichaTecnicaService fichaTecnicaService;
    private final ConfiguracaoPrecificacaoRepository configuracaoPrecificacaoRepository;
    private final ProdutoMapper produtoMapper;
    private final UsuarioRepository usuarioRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoCustomizacaoRepository itemCatalogoCustomizacaoRepository;
    private final ItemCatalogoService itemCatalogoService;

    /**
     * #135/RN-039 — custoUnitario recalculado ao vivo por item da página, mesmo cálculo de buscarPorId
     * (antes ficava travado em produto.precoCusto, só atualizado em cadastrar/editar — desatualizava se
     * o preço de um insumo da ficha técnica mudasse depois). Custo aceito: N+1 (ficha técnica + valor-hora
     * por item), mesmo critério já usado em GET /producoes (página limitada a 20 itens por padrão).
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listar(TipoProduto tipo, String busca, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        boolean temBusca = busca != null && !busca.isBlank();
        Page<Produto> pagina;
        if (tipo != null && temBusca) {
            pagina = produtoRepository.findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndDeletedAtIsNull(usuarioId, tipo, busca, pageable);
        } else if (tipo != null) {
            pagina = produtoRepository.findByUsuarioIdAndTipoAndDeletedAtIsNull(usuarioId, tipo, pageable);
        } else if (temBusca) {
            pagina = produtoRepository.findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(usuarioId, busca, pageable);
        } else {
            pagina = produtoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable);
        }

        BigDecimal valorHora = buscarValorHora(usuarioId);
        return pagina.map(produto -> montarResponseComCustoAoVivo(produto, valorHora));
    }

    /**
     * Frente 4/P-BE-CONSOLIDADO-001 — badges de filtro de ListaProdutosPage.tsx (ProductCard/categoria
     * mostravam sempre 0 pras categorias fora do filtro ativo, porque o dado não existia na API).
     * Ignora o filtro de `busca` da tela (decisão do prompt de origem: badges de categoria são
     * navegação global) — conta todos os produtos do usuário (deletedAt IS NULL), sem paginação.
     */
    @Transactional(readOnly = true)
    public ProdutoContagensResponse contagens() {
        UUID usuarioId = getUsuarioIdAutenticado();

        ProdutoContagensResponse response = new ProdutoContagensResponse();
        response.setTotal(produtoRepository.countByUsuarioIdAndDeletedAtIsNull(usuarioId));
        response.setInativos(produtoRepository.countByUsuarioIdAndAtivoFalseAndDeletedAtIsNull(usuarioId));

        ProdutoContagensResponse.PorTipo porTipo = new ProdutoContagensResponse.PorTipo();
        for (ProdutoRepository.ContagemPorTipo contagem : produtoRepository.contarPorTipo(usuarioId)) {
            switch (contagem.getTipo()) {
                case PRODUTO -> porTipo.setProduto(contagem.getQuantidade());
                case CUSTOMIZACAO -> porTipo.setCustomizacao(contagem.getQuantidade());
            }
        }
        response.setPorTipo(porTipo);
        return response;
    }

    private ProdutoResponse montarResponseComCustoAoVivo(Produto produto, BigDecimal valorHora) {
        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        ProdutoResponse response = produtoMapper.toResponse(produto, itens);
        BigDecimal somaComponentes = fichaTecnicaService.recalcularPrecoCusto(produto.getId());
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        response.setCustoUnitario(calcularCustoUnitario(custoTotalLote, produto.getRendimento()));
        return response;
    }

    @Transactional(readOnly = true)
    public ProdutoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        ProdutoDetalheResponse response = produtoMapper.toDetalheResponse(produto, itens);

        BigDecimal somaComponentes = fichaTecnicaService.recalcularPrecoCusto(produto.getId());
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        response.setCustoTotalLote(custoTotalLote);

        // custoUnitario recalculado ao vivo (não usa produto.getPrecoCusto(), que só é atualizado no save
        // e fica stale se um insumo/produto base/valorHora mudar depois — bug RN-039 investigado).
        BigDecimal custoUnitario = calcularCustoUnitario(custoTotalLote, produto.getRendimento());
        response.setCustoUnitario(custoUnitario);

        response.setPrecoSugerido(calcularPrecoSugerido(custoUnitario, produto.getMargemLucro()));
        return response;
    }

    public ProdutoDetalheResponse cadastrar(ProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        validarRendimento(request);

        Usuario usuario = getUsuarioAutenticado();
        Produto produto = produtoMapper.toEntity(request, usuario);
        // #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition.
        usuarioRepository.lockPorId(usuarioId);
        produto.setNumero(NumeroSequencialUtil.proximoNumero(
                produtoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Produto::getNumero)));
        if (produto.getPrecoVenda() == null) {
            // placeholder só para satisfazer chk_preco_venda_tipo no INSERT inicial (precisa do id do
            // produto para persistir a ficha técnica antes de calcular o precoSugerido real) — sobrescrito abaixo
            produto.setPrecoVenda(BigDecimal.ZERO);
        }
        produto = produtoRepository.save(produto);

        BigDecimal somaComponentes = fichaTecnicaService.salvarFichaTecnica(produto, request.getFichaTecnica(), usuarioId);
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        BigDecimal custoUnitario = calcularCustoUnitario(custoTotalLote, produto.getRendimento());
        produto.setPrecoCusto(custoUnitario);

        if (produto.getMargemLucro() == null) {
            produto.setMargemLucro(buscarMargemPadrao(usuarioId));
        }
        BigDecimal precoSugerido = calcularPrecoSugerido(custoUnitario, produto.getMargemLucro());
        aplicarPrecoVendaCriacao(produto, request.getPrecoVenda(), precoSugerido);
        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO) {
            validarPrecoVendaObrigatorio(produto);
        }

        produto = produtoRepository.save(produto);

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        ProdutoDetalheResponse response = produtoMapper.toDetalheResponse(produto, itens);
        response.setCustoTotalLote(custoTotalLote);
        response.setPrecoSugerido(precoSugerido);
        return response;
    }

    public ProdutoDetalheResponse editar(UUID id, ProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        validarRendimento(request);

        BigDecimal precoVendaAntigo = produto.getPrecoVenda();
        BigDecimal margemLucroAntigo = produto.getMargemLucro();
        boolean overrideAntigo = Boolean.TRUE.equals(produto.getOverride());

        produtoMapper.updateEntity(request, produto);
        if (produto.getMargemLucro() == null) {
            // margemLucro não veio no request: preserva o valor anterior (não tem conceito de override próprio)
            produto.setMargemLucro(margemLucroAntigo);
        }

        BigDecimal somaComponentes = fichaTecnicaService.salvarFichaTecnica(produto, request.getFichaTecnica(), usuarioId);
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        BigDecimal custoUnitario = calcularCustoUnitario(custoTotalLote, produto.getRendimento());
        produto.setPrecoCusto(custoUnitario);

        BigDecimal precoSugerido = calcularPrecoSugerido(custoUnitario, produto.getMargemLucro());
        boolean margemMudou = !valoresIguais(margemLucroAntigo, produto.getMargemLucro());
        aplicarPrecoVendaEdicao(produto, request.getPrecoVenda(), precoSugerido, precoVendaAntigo, overrideAntigo, margemMudou);
        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO) {
            validarPrecoVendaObrigatorio(produto);
        }

        produtoRepository.save(produto);

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        ProdutoDetalheResponse response = produtoMapper.toDetalheResponse(produto, itens);
        response.setCustoTotalLote(custoTotalLote);
        response.setPrecoSugerido(precoSugerido);
        return response;
    }

    /**
     * Frente 5/P-BE-CONSOLIDADO-001 (Opção A confirmada em 2026-07-29) — renomeado de {@code inativar()}
     * pra {@code excluir()} pra desambiguar do toggle reversível novo ({@link #inativar(UUID)}/
     * {@link #reativar(UUID)}): DELETE /produtos/{id} sempre foi remoção lógica total (deletedAt), o
     * produto some da listagem inteira — não é "marcar como inativo". O nome antigo do método
     * confundia as duas operações; comportamento inalterado, só o nome mudou.
     */
    /** #237/PDT-0XX — DELETE também passa a checar vínculos (Catálogo e ficha técnica), igual a {@link #inativar(UUID)}. */
    public void excluir(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        validarSemVinculos(produto);
        produto.setDeletedAt(LocalDateTime.now());
        produtoRepository.save(produto);
    }

    /**
     * Frente 5/P-BE-CONSOLIDADO-001 — inativação reversível (Opção A): {@code ativo=false}, produto
     * continua existindo (deletedAt permanece null) e continua aparecendo em GET /produtos, mas fica
     * bloqueado pra novo uso — RN-045 (venda via Catálogo em orçamento) já checava
     * {@code produto.getAtivo()} antes deste prompt, então o bloqueio de venda já funcionava; só
     * faltava um jeito de setar {@code ativo=false} de verdade. Idempotente: inativar produto já
     * inativo não lança erro.
     */
    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        validarSemVinculos(produto);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    /**
     * #237/PDT-0XX — bloqueia se o produto estiver vinculado a algum Catálogo (produto principal ou
     * customização anexada, já coberto por {@link #listarCatalogosVinculados(UUID)}) OU se for usado
     * como {@code produtoBase} na ficha técnica de outro produto (RN-NOVA-1/#210 — Produto pode ser
     * componente de Produto).
     */
    private void validarSemVinculos(Produto produto) {
        List<Catalogo> catalogosVinculados = listarCatalogosVinculados(produto.getId());
        List<Produto> produtosQueUsamComoComponente = fichaTecnicaItemRepository.findProdutosByProdutoBaseId(produto.getId());
        if (catalogosVinculados.isEmpty() && produtosQueUsamComoComponente.isEmpty()) {
            return;
        }
        List<String> partes = new ArrayList<>();
        if (!catalogosVinculados.isEmpty()) {
            partes.add("catálogo(s): " + catalogosVinculados.stream().map(Catalogo::getNome).collect(Collectors.joining(", ")));
        }
        if (!produtosQueUsamComoComponente.isEmpty()) {
            partes.add("ficha técnica de: " + produtosQueUsamComoComponente.stream().map(Produto::getNome).collect(Collectors.joining(", ")));
        }
        throw new BusinessException("Produto " + produto.getNome() + " está vinculado a " + String.join(" e ", partes)
                + ". Resolva os vínculos (POST /produtos/{id}/resolver-vinculos) antes de continuar.");
    }

    /**
     * PDT-013 — catálogos (não excluídos) que referenciam o produto, como produto principal de um
     * item de catálogo ou como customização anexada a um item de catálogo de outro produto. União
     * distinta por catálogo — o mesmo catálogo pode referenciar o produto nos dois papéis.
     */
    @Transactional(readOnly = true)
    public List<CatalogoVinculadoResponse> catalogosVinculados(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return listarCatalogosVinculados(id).stream().map(produtoMapper::toCatalogoVinculadoResponse).toList();
    }

    /**
     * PDT-0XX — produtos (não excluídos) cuja ficha técnica usa este produto como {@code produtoBase}
     * (componente), com o {@code vinculoId} (id de {@code FichaTecnicaItem}) exigido por
     * {@link #resolverVinculos(UUID, ResolverVinculosProdutoRequest)} na ação SUBSTITUIR.
     */
    @Transactional(readOnly = true)
    public List<ComponenteVinculadoResponse> componentesVinculados(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return fichaTecnicaItemRepository.findByProdutoBaseId(id).stream()
                .map(produtoMapper::toComponenteVinculadoResponse)
                .toList();
    }

    private List<Catalogo> listarCatalogosVinculados(UUID produtoId) {
        Map<UUID, Catalogo> catalogosPorId = new LinkedHashMap<>();
        itemCatalogoRepository.findByProdutoIdAndDeletedAtIsNull(produtoId)
                .forEach(item -> catalogosPorId.putIfAbsent(item.getCatalogo().getId(), item.getCatalogo()));
        itemCatalogoCustomizacaoRepository.findItensCatalogoPorProdutoComoCustomizacao(produtoId)
                .forEach((ItemCatalogo item) -> catalogosPorId.putIfAbsent(item.getCatalogo().getId(), item.getCatalogo()));
        return List.copyOf(catalogosPorId.values());
    }

    /**
     * #237 (V0.7, correção de contrato) — resolução em massa dos vínculos que bloqueiam
     * {@link #inativar(UUID)}/{@link #excluir(UUID)}, agrupados em 2 blocos independentes com ação
     * própria cada um: {@code catalogo} (item de catálogo principal + customização anexada) e
     * {@code componente} (produto usado como {@code produtoBase} na ficha técnica de outro produto).
     * Cada bloco só é obrigatório no request se o produto de fato tiver vínculo daquele tipo — bloco
     * ausente quando há vínculo pendente daquele tipo é erro; bloco presente sem vínculo daquele tipo
     * é ignorado. {@code REMOVER_VINCULOS} remove só o vínculo específico daquele bloco (ItemCatalogo
     * não tem campo {@code ativo} — a única forma de "desligar" um vínculo pontual é soft-delete do
     * item / remoção da linha de customização ou componente, sem tocar outros itens do mesmo
     * catálogo/produto). Depois de resolver os blocos presentes, executa a operação original
     * ({@code operacao}) na mesma chamada. Transação única (classe é {@code @Transactional}) — sem
     * aplicação parcial mesmo com ações diferentes por bloco.
     */
    public void resolverVinculos(UUID id, ResolverVinculosProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        List<ItemCatalogo> itensPrincipal = itemCatalogoRepository.findByProdutoIdAndDeletedAtIsNull(id);
        List<ItemCatalogoCustomizacao> customizacoesAnexadas = itemCatalogoCustomizacaoRepository.findByProdutoId(id);
        List<FichaTecnicaItem> componentesEmOutrosProdutos = fichaTecnicaItemRepository.findByProdutoBaseId(id);

        boolean temVinculoCatalogo = !itensPrincipal.isEmpty() || !customizacoesAnexadas.isEmpty();
        boolean temVinculoComponente = !componentesEmOutrosProdutos.isEmpty();

        if (!temVinculoCatalogo && !temVinculoComponente) {
            throw new BusinessException("Produto " + produto.getNome() + " não possui vínculos pendentes de resolução.");
        }
        if (temVinculoCatalogo && request.getCatalogo() == null) {
            throw new BusinessException("Produto " + produto.getNome()
                    + " possui vínculo de catálogo pendente — o bloco \"catalogo\" é obrigatório.");
        }
        if (temVinculoComponente && request.getComponente() == null) {
            throw new BusinessException("Produto " + produto.getNome()
                    + " possui vínculo de componente de ficha técnica pendente — o bloco \"componente\" é obrigatório.");
        }

        if (temVinculoCatalogo) {
            resolverVinculoCatalogo(usuarioId, itensPrincipal, customizacoesAnexadas, request.getCatalogo());
        }
        if (temVinculoComponente) {
            resolverVinculoComponente(usuarioId, componentesEmOutrosProdutos, request.getComponente());
        }

        switch (request.getOperacao()) {
            case INATIVAR -> produto.setAtivo(false);
            case EXCLUIR -> produto.setDeletedAt(LocalDateTime.now());
        }
        produtoRepository.save(produto);
    }

    private void resolverVinculoCatalogo(UUID usuarioId, List<ItemCatalogo> itensPrincipal,
                                          List<ItemCatalogoCustomizacao> customizacoesAnexadas,
                                          ResolucaoVinculoCatalogoRequest request) {
        if (request.getAcao() == AcaoResolucaoVinculo.REMOVER_VINCULOS) {
            removerVinculosCatalogo(itensPrincipal, customizacoesAnexadas);
        } else {
            aplicarSubstituicoesCatalogo(usuarioId, itensPrincipal, customizacoesAnexadas, request.getSubstituicoes());
        }
    }

    private void removerVinculosCatalogo(List<ItemCatalogo> itensPrincipal, List<ItemCatalogoCustomizacao> customizacoesAnexadas) {
        LocalDateTime agora = LocalDateTime.now();
        itensPrincipal.forEach(item -> item.setDeletedAt(agora));
        itemCatalogoRepository.saveAll(itensPrincipal);

        itemCatalogoCustomizacaoRepository.deleteAll(customizacoesAnexadas);
    }

    private void aplicarSubstituicoesCatalogo(UUID usuarioId, List<ItemCatalogo> itensPrincipal,
                                               List<ItemCatalogoCustomizacao> customizacoesAnexadas,
                                               List<SubstituicaoVinculoProdutoRequest> substituicoes) {
        List<SubstituicaoVinculoProdutoRequest> lista = substituicoes != null ? substituicoes : List.of();
        Map<UUID, SubstituicaoVinculoProdutoRequest> porVinculoId = new HashMap<>();
        for (SubstituicaoVinculoProdutoRequest sub : lista) {
            porVinculoId.put(sub.getVinculoId(), sub);
        }

        for (ItemCatalogo item : itensPrincipal) {
            SubstituicaoVinculoProdutoRequest sub = exigirSubstituicao(porVinculoId, item.getId(),
                    TipoVinculoProduto.ITEM_CATALOGO_PRINCIPAL, "item de catálogo");
            Produto novoProduto = buscarProdutoDoUsuario(sub.getNovoProdutoId(), usuarioId);
            validarProdutoTemCusto(novoProduto);
            substituirProdutoPrincipal(item, novoProduto);
        }

        for (ItemCatalogoCustomizacao customizacao : customizacoesAnexadas) {
            SubstituicaoVinculoProdutoRequest sub = exigirSubstituicao(porVinculoId, customizacao.getId(),
                    TipoVinculoProduto.CUSTOMIZACAO_ANEXADA, "customização anexada");
            Produto novoProduto = buscarProdutoDoUsuario(sub.getNovoProdutoId(), usuarioId);
            if (novoProduto.getTipo() != TipoProduto.CUSTOMIZACAO) {
                throw new BusinessException("O produto substituto deve ser do tipo Customização.");
            }
            substituirCustomizacaoAnexada(customizacao, novoProduto);
        }
    }

    private SubstituicaoVinculoProdutoRequest exigirSubstituicao(Map<UUID, SubstituicaoVinculoProdutoRequest> porVinculoId,
                                                                  UUID vinculoId, TipoVinculoProduto tipoEsperado, String descricao) {
        SubstituicaoVinculoProdutoRequest sub = porVinculoId.get(vinculoId);
        if (sub == null || sub.getTipo() != tipoEsperado) {
            throw new BusinessException("Falta substituição para o vínculo de " + descricao + " (id " + vinculoId + ").");
        }
        return sub;
    }

    private void resolverVinculoComponente(UUID usuarioId, List<FichaTecnicaItem> componentesEmOutrosProdutos,
                                            ResolucaoVinculoComponenteRequest request) {
        if (request.getAcao() == AcaoResolucaoVinculo.REMOVER_VINCULOS) {
            removerVinculosComponente(componentesEmOutrosProdutos);
        } else {
            aplicarSubstituicoesComponente(usuarioId, componentesEmOutrosProdutos, request.getSubstituicoes());
        }
    }

    private void removerVinculosComponente(List<FichaTecnicaItem> componentesEmOutrosProdutos) {
        Set<UUID> produtosPaisAfetados = componentesEmOutrosProdutos.stream()
                .map(item -> item.getProduto().getId()).collect(Collectors.toSet());
        fichaTecnicaItemRepository.deleteAll(componentesEmOutrosProdutos);
        produtosPaisAfetados.forEach(this::recalcularPrecoCustoPersistido);
    }

    private void aplicarSubstituicoesComponente(UUID usuarioId, List<FichaTecnicaItem> componentesEmOutrosProdutos,
                                                 List<SubstituicaoComponenteVinculoRequest> substituicoes) {
        List<SubstituicaoComponenteVinculoRequest> lista = substituicoes != null ? substituicoes : List.of();
        Map<UUID, SubstituicaoComponenteVinculoRequest> porVinculoId = new HashMap<>();
        for (SubstituicaoComponenteVinculoRequest sub : lista) {
            porVinculoId.put(sub.getVinculoId(), sub);
        }

        for (FichaTecnicaItem componente : componentesEmOutrosProdutos) {
            SubstituicaoComponenteVinculoRequest sub = porVinculoId.get(componente.getId());
            if (sub == null) {
                throw new BusinessException("Falta substituição para o vínculo de componente de ficha técnica (id " + componente.getId() + ").");
            }
            Produto novoProdutoBase = buscarProdutoDoUsuario(sub.getNovoProdutoId(), usuarioId);
            if (novoProdutoBase.getTipo() != TipoProduto.PRODUTO || !Boolean.TRUE.equals(novoProdutoBase.getAtivo())) {
                throw new BusinessException("Apenas produtos ativos do tipo Produto podem ser usados como componente de ficha técnica.");
            }
            componente.setProdutoBase(novoProdutoBase);
            fichaTecnicaItemRepository.save(componente);
            recalcularPrecoCustoPersistido(componente.getProduto().getId());
        }
    }

    /** Padrão calculado+override (RN-038a/RN-042): só recalcula precoVenda se o item não estiver em override. */
    private void substituirProdutoPrincipal(ItemCatalogo item, Produto novoProduto) {
        List<ItemCatalogoCustomizacao> customizacoesDoItem = itemCatalogoCustomizacaoRepository.findByItemCatalogoId(item.getId());
        item.setProduto(novoProduto);
        BigDecimal precoSugerido = itemCatalogoService.calcularPrecoSugerido(
                novoProduto, item.getQuantidadePacote(), customizacoesDoItem, item.getCatalogo().getMargem());
        if (!Boolean.TRUE.equals(item.getOverride())) {
            item.setPrecoVenda(precoSugerido);
        }
        itemCatalogoRepository.save(item);
    }

    private void substituirCustomizacaoAnexada(ItemCatalogoCustomizacao customizacao, Produto novoProduto) {
        customizacao.setProduto(novoProduto);
        itemCatalogoCustomizacaoRepository.save(customizacao);

        ItemCatalogo item = customizacao.getItemCatalogo();
        List<ItemCatalogoCustomizacao> todasCustomizacoes = itemCatalogoCustomizacaoRepository.findByItemCatalogoId(item.getId());
        BigDecimal precoSugerido = itemCatalogoService.calcularPrecoSugerido(
                item.getProduto(), item.getQuantidadePacote(), todasCustomizacoes, item.getCatalogo().getMargem());
        if (!Boolean.TRUE.equals(item.getOverride())) {
            item.setPrecoVenda(precoSugerido);
        }
        itemCatalogoRepository.save(item);
    }

    private Produto buscarProdutoDoUsuario(UUID produtoId, UUID usuarioId) {
        return produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + produtoId));
    }

    /**
     * #228/#237 — recalcula e persiste {@code produto.precoCusto} a partir da ficha técnica atual, sem
     * tocar {@code precoVenda}/{@code override} (mudança de custo nunca recalcula o preço sozinha —
     * mesma regra de {@link #editar(UUID, ProdutoRequest)}). Usado após substituição/remoção de
     * componente feita fora do fluxo normal de edição (resolução de vínculo de Insumo/Produto).
     */
    public void recalcularPrecoCustoPersistido(UUID produtoId) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        BigDecimal somaComponentes = fichaTecnicaService.recalcularPrecoCusto(produtoId);
        BigDecimal valorHora = buscarValorHora(produto.getUsuario().getId());
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        produto.setPrecoCusto(calcularCustoUnitario(custoTotalLote, produto.getRendimento()));
        produtoRepository.save(produto);
    }

    /**
     * Frente 5/P-BE-CONSOLIDADO-001 — reverte a inativação: {@code ativo=true}. Só atua sobre produto
     * não excluído (deletedAt null) — reativar produto excluído não é suportado, exclusão é permanente
     * (RN não permite "desexcluir"; é preciso recriar o produto). Idempotente: reativar produto já
     * ativo não lança erro.
     */
    public void reativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(true);
        produtoRepository.save(produto);
    }

    /**
     * RN-054 — preço sugerido de um produto avulso (sem Catálogo) dada uma margem informada na hora.
     * Reaproveita o custo_unitario já calculado e persistido no produto (RN-039) — não recalcula ficha técnica.
     */
    @Transactional(readOnly = true)
    public PrecoSugeridoResponse calcularPrecoSugeridoAvulso(UUID produtoId, BigDecimal margem) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        BigDecimal precoSugerido = calcularPrecoSugerido(produto.getPrecoCusto(), margem);
        return new PrecoSugeridoResponse(produto.getPrecoCusto(), margem, precoSugerido);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoProdutoResponse> listarMovimentacoes(UUID produtoId, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return movimentacaoProdutoRepository.findByProdutoIdOrderByCreatedAtDesc(produtoId, pageable)
                .map(produtoMapper::toMovimentacaoResponse);
    }

    public MovimentacaoProdutoResponse baixaManual(UUID produtoId, BaixaManualProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        BigDecimal estoqueResultante = produto.getEstoqueAtual().subtract(request.getQuantidade());
        if (estoqueResultante.compareTo(BigDecimal.ZERO) < 0 && !produto.getPermitirEstoqueNegativo()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + produto.getNome() + ". Este produto não permite estoque negativo.");
        }

        produto.setEstoqueAtual(estoqueResultante);
        produtoRepository.save(produto);

        MovimentacaoProduto movimentacao = MovimentacaoProduto.builder()
                .produto(produto)
                .tipo(TipoMovimentacaoProduto.SAIDA)
                .motivo(request.getMotivo())
                .quantidade(request.getQuantidade())
                .observacao(request.getObservacao())
                .estornada(false)
                .build();

        return produtoMapper.toMovimentacaoResponse(movimentacaoProdutoRepository.save(movimentacao));
    }

    // ---------------------------------------------------------------
    // RN-039 — Custo Total do lote / Custo Unitário
    // ---------------------------------------------------------------

    private void validarRendimento(ProdutoRequest request) {
        boolean temFichaTecnica = request.getFichaTecnica() != null && !request.getFichaTecnica().isEmpty();
        if (temFichaTecnica && (request.getRendimento() == null || request.getRendimento().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("Rendimento é obrigatório e deve ser maior que zero quando a ficha técnica está preenchida.");
        }
    }

    private BigDecimal calcularCustoMaoDeObra(Integer tempoProducaoMinutos, BigDecimal valorHora) {
        return BigDecimal.valueOf(tempoProducaoMinutos)
                .divide(SESSENTA, 6, RoundingMode.HALF_UP)
                .multiply(valorHora);
    }

    private BigDecimal calcularCustoUnitario(BigDecimal custoTotalLote, BigDecimal rendimento) {
        // rendimento nulo/zero só é possível quando a ficha técnica está vazia (validarRendimento não bloqueou);
        // nesse caso não há divisão por lote a fazer — custo unitário = custo total.
        BigDecimal divisor = (rendimento != null && rendimento.compareTo(BigDecimal.ZERO) > 0) ? rendimento : BigDecimal.ONE;
        return custoTotalLote.divide(divisor, 4, RoundingMode.HALF_UP);
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

    // ---------------------------------------------------------------
    // RN-038a — Preço de venda com override (PRODUTO e CUSTOMIZACAO, #210+231+234)
    // ---------------------------------------------------------------

    private BigDecimal calcularPrecoSugerido(BigDecimal custoUnitario, BigDecimal margemLucro) {
        BigDecimal margem = margemLucro != null ? margemLucro : BigDecimal.ZERO;
        BigDecimal fator = BigDecimal.ONE.add(margem.divide(CEM, 6, RoundingMode.HALF_UP));
        return custoUnitario.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    private void aplicarPrecoVendaCriacao(Produto produto, BigDecimal precoVendaInformado, BigDecimal precoSugerido) {
        if (precoVendaInformado != null && precoVendaInformado.compareTo(precoSugerido) != 0) {
            produto.setPrecoVenda(precoVendaInformado);
            produto.setOverride(true);
        } else {
            produto.setPrecoVenda(precoSugerido);
            produto.setOverride(false);
        }
    }

    private void aplicarPrecoVendaEdicao(Produto produto, BigDecimal precoVendaInformado, BigDecimal precoSugerido,
                                          BigDecimal precoVendaAntigo, boolean overrideAntigo, boolean margemMudou) {
        if (precoVendaInformado != null && precoVendaInformado.compareTo(precoSugerido) != 0) {
            // artesã editou o preço manualmente para um valor diferente do sugerido
            produto.setPrecoVenda(precoVendaInformado);
            produto.setOverride(true);
            return;
        }
        if (precoVendaInformado != null) {
            // veio igual ao sugerido: trata como se não fosse override
            produto.setPrecoVenda(precoSugerido);
            produto.setOverride(false);
            return;
        }
        // precoVenda não veio no request
        if (margemMudou && !overrideAntigo) {
            // sem override: acompanha a nova margem
            produto.setPrecoVenda(precoSugerido);
            produto.setOverride(false);
            return;
        }
        // com override, ou mudança apenas de custo (ficha técnica/insumo): preço persistido nunca muda sozinho
        produto.setPrecoVenda(precoVendaAntigo);
        produto.setOverride(overrideAntigo);
    }

    private void validarPrecoVendaObrigatorio(Produto produto) {
        if (produto.getPrecoVenda() == null || produto.getPrecoVenda().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Preço de venda é obrigatório para produtos do tipo Customização.");
        }
    }

    /** RN-044 — mesma validação de {@code ItemCatalogoService.validarProdutoTemCusto}, reaplicada na substituição de vínculo. */
    private void validarProdutoTemCusto(Produto produto) {
        if (produto.getPrecoCusto() == null || produto.getPrecoCusto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "O produto não possui custo calculado. Complete o cadastro do produto (ficha técnica e rendimento) antes de usá-lo.");
        }
    }

    private boolean valoresIguais(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    private UUID getUsuarioIdAutenticado() {
        return getUsuarioAutenticado().getId();
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
