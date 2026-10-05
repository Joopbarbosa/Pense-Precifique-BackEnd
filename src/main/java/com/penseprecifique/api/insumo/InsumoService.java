package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.enums.RegraPrecoReferencia;
import com.penseprecifique.api.compra.FornecedorInsumoService;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.compra.CompraItemRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRascunhoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.ResolverVinculosInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.ResolucaoVinculoCatalogoInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.ResolucaoVinculoFichaTecnicaInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.SubstituicaoInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.SubstituicaoVinculoCatalogoInsumoRequestDTO;
import com.penseprecifique.api.catalogo.ItemCatalogoComponenteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoContagensResponse;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.ProdutoRelacionadoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.InsumoMapper;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.FichaTecnicaService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.produto.ProdutoService;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InsumoService {

    // #354 — allowlist explícita dos campos de ordenação aceitos em GET /insumos. Campo fora desta
    // lista é rejeitado com BusinessException (400) por PageableOrdenacaoResolver, nunca mais
    // repassado cru pro Hibernate (UnknownPathException → 500).
    private static final Map<String, String> CAMPOS_ORDENACAO_INSUMO = Map.of(
            "nome", "nome",
            "numero", "numero",
            "custoUnitario", "custoUnitario",
            "estoqueAtual", "estoqueAtual",
            "createdAt", "createdAt"
    );

    private final InsumoRepository insumoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final UsuarioRepository usuarioRepository;
    private final InsumoMapper insumoMapper;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final FichaTecnicaService fichaTecnicaService;
    private final ProducaoRepository producaoRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;
    private final ItemCatalogoComponenteRepository itemCatalogoComponenteRepository;
    private final ItemCatalogoService itemCatalogoService;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final CompraRepository compraRepository;
    private final CompraItemRepository compraItemRepository;
    private final VendaCaixaRepository vendaCaixaRepository;
    private final FornecedorInsumoService fornecedorInsumoService;

    @Transactional(readOnly = true)
    public Page<InsumoResponseDTO> listar(String busca, Boolean ativo, Pageable pageable) {
        return listar(busca, ativo, false, pageable);
    }

    public Page<InsumoResponseDTO> listar(String busca, Boolean ativo, boolean incluirInativos, Pageable pageable) {
        return listar(busca, ativo, incluirInativos, false, pageable);
    }

    /**
     * #616/RN-NOVA-40 (adendo 2) — {@code incluirInativos}: ativos e inativos, ativos primeiro (seletores
     * mostram o inativo riscado, sem poder escolher); ignora {@code ativo}.
     * V0.16.0 (#687, DT-NOVA-12) — rascunho fica fora por padrão (seletores de ficha, catálogo e
     * orçamento); a listagem de Insumos e os seletores da compra pedem {@code incluirRascunhos}.
     */
    public Page<InsumoResponseDTO> listar(String busca, Boolean ativo, boolean incluirInativos,
                                          boolean incluirRascunhos, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Pageable pageableOrdenado = PageableOrdenacaoResolver.resolver(pageable, CAMPOS_ORDENACAO_INSUMO,
                "nome, numero, custoUnitario, estoqueAtual, createdAt");
        if (incluirInativos) {
            ativo = null;
            pageableOrdenado = PageRequest.of(pageableOrdenado.getPageNumber(), pageableOrdenado.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "ativo").and(pageableOrdenado.getSort()));
        }

        // #336 (V0.10.0) — filtro de status agora é server-side (era client-side sobre a janela
        // paginada, causa raiz confirmada de "insumo inativado não aparece no filtro de inativados").
        String buscaNormalizada = (busca != null && !busca.isBlank()) ? busca : null;
        Page<Insumo> pagina = insumoRepository.buscarComFiltros(usuarioId, buscaNormalizada, ativo, incluirRascunhos, pageableOrdenado);

        Page<InsumoResponseDTO> mapeado = pagina.map(insumoMapper::toResponse);
        return new PageImpl<>(mapeado.getContent(), pageable, mapeado.getTotalElements());
    }

    // RN-NOVA-4 (V0.10.0, #336) — contadores agregados no backend, endpoint separado (GET
    // /insumos, o Page<> nativo do Spring, não carrega campo extra sem quebrar contrato — DT-NOVA-1
    // revisado em DECISOES_V0.10.0.md). Chamado 1x por carregamento de tela, não por filtro clicado.
    @Transactional(readOnly = true)
    public InsumoContagensResponse contagens() {
        UUID usuarioId = getUsuarioIdAutenticado();
        return new InsumoContagensResponse(
                insumoRepository.countByUsuarioIdAndDeletedAtIsNull(usuarioId),
                insumoRepository.countByUsuarioIdAndAtivoAndDeletedAtIsNull(usuarioId, true),
                insumoRepository.countByUsuarioIdAndAtivoAndDeletedAtIsNull(usuarioId, false),
                insumoRepository.contarEstoqueBaixo(usuarioId),
                insumoRepository.countByUsuarioIdAndDeletedAtIsNullAndEstoqueAtualLessThan(usuarioId, BigDecimal.ZERO),
                insumoRepository.countByUsuarioIdAndDeletedAtIsNullAndEstoqueAtualGreaterThan(usuarioId, BigDecimal.ZERO)
        );
    }

    @Transactional(readOnly = true)
    public InsumoResponseDTO buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        return insumoMapper.toResponse(insumo);
    }

    public InsumoResponseDTO cadastrar(InsumoCreateRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        String marca = validarMarca(request.marca(), Boolean.TRUE.equals(request.qualquerMarca()));
        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
                request.nome(), marca, usuarioId)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        Usuario usuario = getUsuarioAutenticado();
        UnidadeMedida unidadeMedida = buscarUnidadeMedidaDoUsuario(request.unidadeMedidaId(), usuarioId);
        Insumo insumo = insumoMapper.toEntity(request, usuario, unidadeMedida);
        insumo.setMarca(marca);
        // #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition.
        usuarioRepository.lockPorId(usuarioId);
        insumo.setNumero(NumeroSequencialUtil.proximoNumero(
                insumoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Insumo::getNumero)));

        // RN-NOVA-1 (V0.10.0, #442, altera INS-003) — cadastro de insumo NÃO gera mais
        // MovimentacaoInsumo/compra automáticos. Custo unitário é calculado e persistido direto
        // a partir do custo e quantidade informados (mesma fórmula/escala de
        // CustoMedioPonderado (ex-LoteCompraService#registrarCompraIndividual) para o caso trivial de insumo novo, sem estoque
        // anterior a ponderar) — estoqueAtual permanece 0 (default da entidade), sem histórico de
        // movimentação. Entrada de estoque real passa a exigir sempre "Registrar compra" ou "Entrada
        // manual", igual a qualquer entrada subsequente.
        BigDecimal custoUnitario = request.precoTotalCompraInicial()
                .divide(request.quantidadeCompradaInicial(), 6, RoundingMode.HALF_UP);
        insumo.setCustoUnitario(custoUnitario);

        insumo = insumoRepository.save(insumo);

        return insumoMapper.toResponse(insumo);
    }

    /**
     * V0.16.0 (#687, RN-NOVA-18, UC-NOVO-4) — insumo em RASCUNHO a partir do item da nota: unidade só
     * quando a sigla da nota coincide com uma unidade cadastrada (sem diferenciar maiúscula), custo
     * proposto = valor final ÷ quantidade só nesse caso; estoque zero, sem movimentação. Com
     * {@code simular}, devolve a proposta sem gravar (a modal mostra antes de salvar).
     */
    public InsumoResponseDTO criarRascunho(InsumoRascunhoRequestDTO request, boolean simular) {
        UUID usuarioId = getUsuarioIdAutenticado();
        String nome = request.nome().trim();
        String marca = request.marca() != null && !request.marca().isBlank() ? request.marca().trim() : null;
        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(nome, marca, usuarioId)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }
        UnidadeMedida unidade = request.unidadeNota() == null || request.unidadeNota().isBlank() ? null
                : unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(
                        usuarioId, request.unidadeNota().trim()).orElse(null);
        BigDecimal custo = BigDecimal.ZERO;
        if (unidade != null && request.quantidadeNota() != null && request.valorFinalNota() != null) {
            custo = request.valorFinalNota().divide(request.quantidadeNota(), 6, RoundingMode.HALF_UP);
        }
        Insumo insumo = Insumo.builder()
                .usuario(getUsuarioAutenticado())
                .nome(nome)
                .marca(marca)
                .unidadeMedida(unidade)
                .custoUnitario(custo)
                .estoqueAtual(BigDecimal.ZERO)
                .rascunho(true)
                .ativo(true)
                .build();
        if (simular) {
            insumo.setNumero(0);
            return insumoMapper.toResponse(insumo);
        }
        usuarioRepository.lockPorId(usuarioId);
        insumo.setNumero(NumeroSequencialUtil.proximoNumero(
                insumoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Insumo::getNumero)));
        return insumoMapper.toResponse(insumoRepository.save(insumo));
    }

    public InsumoResponseDTO editar(UUID id, InsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        boolean qualquerMarca = request.qualquerMarca() != null ? request.qualquerMarca()
                : Boolean.TRUE.equals(insumo.getQualquerMarca());
        String marca = validarMarca(request.marca(), qualquerMarca);
        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndIdNotAndDeletedAtIsNull(
                request.nome(), marca, usuarioId, id)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        UnidadeMedida unidadeMedida = buscarUnidadeMedidaDoUsuario(request.unidadeMedidaId(), usuarioId);
        RegraPrecoReferencia regraAntes = insumo.getRegraPrecoReferencia();
        boolean completando = InsumoUtilizavel.rascunho(insumo);
        insumoMapper.updateEntity(request, insumo, unidadeMedida);
        insumo.setMarca(marca);
        if (completando) {
            completarRascunho(insumo, request);
        }
        Insumo salvo = insumoRepository.save(insumo);
        // #590/RN-NOVA-39 — trocar a regra recalcula o preço de referência de todos os fornecedores.
        if (salvo.getRegraPrecoReferencia() != regraAntes) {
            fornecedorInsumoService.recalcularDoInsumo(salvo);
        }
        return insumoMapper.toResponse(salvo);
    }

    private String validarMarca(String marca, boolean qualquerMarca) {
        if (qualquerMarca && marca != null && !marca.isBlank()) {
            throw BusinessException.explicado("Confira a marca do insumo", "O insumo não foi salvo.",
                    "A opção Não validar marca exige que a marca esteja vazia.",
                    "Confirme a remoção da marca ou desmarque Não validar marca.");
        }
        return qualquerMarca || marca == null || marca.isEmpty() ? null : marca;
    }

    /**
     * V0.16.0 (#687, RN-NOVA-18, CEN-NOVO-29) — salvar o cadastro completo (INS-003: unidade, custo e
     * quantidade) tira o insumo do rascunho; o custo vem do preço ÷ quantidade, como no cadastro, e o
     * estoque continua zero, sem movimentação (RN-NOVA-1 da V0.10.0).
     */
    private void completarRascunho(Insumo insumo, InsumoRequestDTO request) {
        if (request.precoTotalCompraInicial() == null || request.quantidadeCompradaInicial() == null) {
            throw BusinessException.explicado("Cadastro incompleto",
                    "Para completar o insumo " + insumo.getNome() + ", informe o preço total e a quantidade da compra.",
                    "O custo do insumo é calculado pelo preço dividido pela quantidade; sem eles o insumo seguiria sem custo.",
                    "Informe, por exemplo, preço 12,50 e quantidade 5 e salve de novo.");
        }
        insumo.setCustoUnitario(request.precoTotalCompraInicial()
                .divide(request.quantidadeCompradaInicial(), 6, RoundingMode.HALF_UP));
        insumo.setRascunho(false);
    }

    private UnidadeMedida buscarUnidadeMedidaDoUsuario(UUID unidadeMedidaId, UUID usuarioId) {
        return unidadeMedidaRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(unidadeMedidaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de medida não encontrada"));
    }

    /** #228/PDT-0XX — DELETE também passa a checar vínculo de ficha técnica, igual a {@link #inativar(UUID)}. */
    public void excluir(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        if (InsumoUtilizavel.rascunho(insumo)) {
            excluirRascunho(insumo, usuarioId);
            return;
        }
        validarSemVinculoFichaTecnica(insumo);
        validarSemVinculoCatalogo(insumo);
        insumo.setDeletedAt(LocalDateTime.now());
        insumoRepository.save(insumo);
    }

    /**
     * V0.16.0 (#687, RN-NOVA-18, CEN-NOVO-52/56) — rascunho não tem ficha, catálogo, orçamento nem
     * estoque: a exclusão só é bloqueada por linha de compra salva (lista as COM-N). A limpeza dos
     * vínculos salvos da conciliação entra com a tabela de vínculos (#681).
     */
    private void excluirRascunho(Insumo insumo, UUID usuarioId) {
        List<String> compras = compraItemRepository
                .findPorInsumosEStatus(usuarioId, List.of(insumo.getId()), StatusCompra.RASCUNHO).stream()
                .map(item -> item.getCompra())
                .distinct()
                .sorted(Comparator.comparing(Compra::getNumero))
                .map(compra -> IdentificadorFormatter.formatar("COM", compra.getNumero()))
                .toList();
        if (!compras.isEmpty()) {
            throw BusinessException.explicado("Insumo em uso em compra",
                    "O insumo " + insumo.getNome() + " está em compra salva e não pode ser excluído: " + String.join(", ", compras) + ".",
                    "Excluir o insumo deixaria a linha da compra sem insumo.",
                    "Abra a compra, troque o insumo da linha ou exclua a linha, e tente excluir de novo.")
                    .comItens(compras);
        }
        insumo.setDeletedAt(LocalDateTime.now());
        insumoRepository.save(insumo);
    }

    /**
     * INS-010 — inativação reversível: {@code ativo=false}, insumo continua existindo (deletedAt
     * permanece null). INS-011 — só é permitida se o insumo não estiver em ficha técnica de nenhum
     * produto não excluído — produto inativado ainda pode voltar a vender, então continua contando
     * como uso. V0.13.0 (#516, RN-NOVA-1) — também não pode estar em uso como componente de Item de
     * Catálogo (vínculo novo, não existia antes desta versão).
     */
    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        // RN-NOVA-18 — rascunho não pode ser inativado (só completado ou excluído).
        if (InsumoUtilizavel.rascunho(insumo)) {
            throw BusinessException.explicado("Insumo em rascunho",
                    "O insumo " + insumo.getNome() + " ainda é um rascunho e não pode ser inativado.",
                    "Rascunho não entra em ficha, orçamento nem estoque; inativá-lo não teria efeito.",
                    "Complete o cadastro do insumo ou exclua o rascunho.");
        }
        validarSemVinculoFichaTecnica(insumo);
        validarSemVinculoCatalogo(insumo);
        insumo.setAtivo(false);
        insumoRepository.save(insumo);
    }

    private void validarSemVinculoCatalogo(Insumo insumo) {
        List<ItemCatalogoComponente> vinculados = itemCatalogoComponenteRepository.findByInsumoId(insumo.getId());
        if (!vinculados.isEmpty()) {
            String nomes = vinculados.stream().map(c -> c.getItemCatalogo().getNome()).collect(Collectors.joining(", "));
            throw new BusinessException("Insumo " + insumo.getNome()
                    + " está vinculado ao(s) item(ns) de catálogo: " + nomes
                    + ". Resolva os vínculos (POST /insumos/{id}/resolver-vinculos) antes de continuar.");
        }
    }

    private void validarSemVinculoFichaTecnica(Insumo insumo) {
        List<Produto> produtosVinculados = fichaTecnicaItemRepository.findProdutosByInsumoId(insumo.getId());
        if (!produtosVinculados.isEmpty()) {
            String nomes = produtosVinculados.stream().map(Produto::getNome).collect(Collectors.joining(", "));
            throw new BusinessException("Insumo " + insumo.getNome()
                    + " está vinculado à ficha técnica de: " + nomes
                    + ". Resolva os vínculos (POST /insumos/{id}/resolver-vinculos) antes de continuar.");
        }
    }

    /**
     * #228/PDT-0XX — resolução em massa dos vínculos de ficha técnica que bloqueiam
     * {@link #inativar(UUID)}/{@link #excluir(UUID)}: inativa todos os produtos vinculados, ou
     * substitui o insumo em cada um deles, e então executa a operação original ({@code operacao}) na
     * mesma chamada. Transação única — sem aplicação parcial.
     */
    public void resolverVinculos(UUID id, ResolverVinculosInsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        List<Produto> vinculadosFichaTecnica = fichaTecnicaItemRepository.findProdutosByInsumoId(id);
        List<ItemCatalogoComponente> vinculadosCatalogo = itemCatalogoComponenteRepository.findByInsumoId(id);

        boolean temVinculoFichaTecnica = !vinculadosFichaTecnica.isEmpty();
        boolean temVinculoCatalogo = !vinculadosCatalogo.isEmpty();

        if (!temVinculoFichaTecnica && !temVinculoCatalogo) {
            throw new BusinessException("Insumo " + insumo.getNome() + " não possui vínculos pendentes de resolução.");
        }
        if (temVinculoFichaTecnica && request.fichaTecnica() == null) {
            throw new BusinessException("Insumo " + insumo.getNome()
                    + " possui vínculo de ficha técnica pendente — o bloco \"fichaTecnica\" é obrigatório.");
        }
        if (temVinculoCatalogo && request.catalogo() == null) {
            throw new BusinessException("Insumo " + insumo.getNome()
                    + " possui vínculo de catálogo pendente — o bloco \"catalogo\" é obrigatório.");
        }

        if (temVinculoFichaTecnica) {
            resolverVinculoFichaTecnica(id, usuarioId, vinculadosFichaTecnica, request.fichaTecnica());
        }
        if (temVinculoCatalogo) {
            resolverVinculoCatalogo(usuarioId, vinculadosCatalogo, request.catalogo());
        }

        switch (request.operacao()) {
            case INATIVAR -> insumo.setAtivo(false);
            case EXCLUIR -> insumo.setDeletedAt(LocalDateTime.now());
        }
        insumoRepository.save(insumo);
    }

    private void resolverVinculoFichaTecnica(UUID insumoId, UUID usuarioId, List<Produto> vinculados,
                                              ResolucaoVinculoFichaTecnicaInsumoRequestDTO request) {
        if (request.acao() == AcaoResolucaoVinculo.REMOVER_VINCULOS) {
            vinculados.forEach(produto -> produto.setAtivo(false));
            produtoRepository.saveAll(vinculados);
        } else {
            aplicarSubstituicoesFichaTecnica(insumoId, usuarioId, vinculados, request.substituicoes());
        }
    }

    private void aplicarSubstituicoesFichaTecnica(UUID insumoId, UUID usuarioId, List<Produto> vinculados,
                                       List<SubstituicaoInsumoRequestDTO> substituicoes) {
        List<SubstituicaoInsumoRequestDTO> lista = substituicoes != null ? substituicoes : List.of();
        Set<UUID> produtoIdsVinculados = vinculados.stream().map(Produto::getId).collect(Collectors.toSet());
        Set<UUID> produtoIdsCobertos = lista.stream().map(SubstituicaoInsumoRequestDTO::produtoId).collect(Collectors.toSet());
        if (!produtoIdsCobertos.containsAll(produtoIdsVinculados)) {
            throw new BusinessException(
                    "As substituições precisam cobrir todos os produtos vinculados ao insumo antes de prosseguir.");
        }

        for (SubstituicaoInsumoRequestDTO substituicao : lista) {
            if (!produtoIdsVinculados.contains(substituicao.produtoId())) {
                continue;
            }
            fichaTecnicaService.substituirInsumoEmProduto(
                    substituicao.produtoId(), insumoId, substituicao.novoInsumoId(), usuarioId);
            produtoService.recalcularPrecoCustoPersistido(substituicao.produtoId());
        }
    }

    /** V0.13.0 (#516, DT-NOVA-1) — vínculo novo: Insumo usado como componente de Item de Catálogo. */
    private void resolverVinculoCatalogo(UUID usuarioId, List<ItemCatalogoComponente> vinculados,
                                          ResolucaoVinculoCatalogoInsumoRequestDTO request) {
        if (request.acao() == AcaoResolucaoVinculo.REMOVER_VINCULOS) {
            itemCatalogoComponenteRepository.deleteAll(vinculados);
            return;
        }
        List<SubstituicaoVinculoCatalogoInsumoRequestDTO> lista = request.substituicoes() != null
                ? request.substituicoes() : List.of();
        Map<UUID, SubstituicaoVinculoCatalogoInsumoRequestDTO> porVinculoId = lista.stream()
                .collect(Collectors.toMap(SubstituicaoVinculoCatalogoInsumoRequestDTO::vinculoId, s -> s));

        for (ItemCatalogoComponente componente : vinculados) {
            SubstituicaoVinculoCatalogoInsumoRequestDTO sub = porVinculoId.get(componente.getId());
            if (sub == null) {
                throw new BusinessException("Falta substituição para o vínculo de componente de item de catálogo (id " + componente.getId() + ").");
            }
            Insumo novoInsumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(sub.novoInsumoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + sub.novoInsumoId()));
            if (!Boolean.TRUE.equals(novoInsumo.getAtivo())) {
                throw new BusinessException("O insumo substituto está inativo e não pode ser usado. Reative-o para continuar.");
            }
            InsumoUtilizavel.exigirNaoRascunho(novoInsumo, "usado como componente de item de catálogo");
            componente.setInsumo(novoInsumo);
            itemCatalogoComponenteRepository.save(componente);
            itemCatalogoService.recalcularAposSubstituicaoComponente(componente.getItemCatalogo().getId());
        }
    }

    /** INS-010 — reverte a inativação: {@code ativo=true}. Idempotente, sem validação adicional. */
    public void reativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        insumo.setAtivo(true);
        insumoRepository.save(insumo);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoInsumoResponseDTO> listarMovimentacoes(UUID insumoId, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        Page<MovimentacaoInsumo> movimentacoes =
                movimentacaoInsumoRepository.findByInsumoIdOrderByCreatedAtDesc(insumoId, pageable);

        Set<UUID> producaoIds = referenciaIdsDoTipo(movimentacoes, ReferenciaMovimentacaoTipo.PRODUCAO);
        Map<UUID, Integer> numeroProducaoPorId = producaoIds.isEmpty() ? Map.of()
                : producaoRepository.findAllById(producaoIds).stream()
                        .collect(Collectors.toMap(Producao::getId, Producao::getNumero));

        Set<UUID> orcamentoIds = referenciaIdsDoTipo(movimentacoes, ReferenciaMovimentacaoTipo.ORCAMENTO);
        Map<UUID, Integer> numeroOrcamentoPorId = orcamentoIds.isEmpty() ? Map.of()
                : orcamentoRepository.findAllById(orcamentoIds).stream()
                        .collect(Collectors.toMap(Orcamento::getId, Orcamento::getNumero));

        // #542/RN-NOVA-7 (V0.15.0) — COM-N e CX-N resolvidos pela mesma técnica de PRD/ORC.
        Set<UUID> compraIds = referenciaIdsDoTipo(movimentacoes, ReferenciaMovimentacaoTipo.COMPRA);
        Map<UUID, Integer> numeroCompraPorId = compraIds.isEmpty() ? Map.of()
                : compraRepository.findAllById(compraIds).stream()
                        .collect(Collectors.toMap(Compra::getId, Compra::getNumero));

        Set<UUID> vendaIds = referenciaIdsDoTipo(movimentacoes, ReferenciaMovimentacaoTipo.CAIXA);
        Map<UUID, Integer> numeroVendaPorId = vendaIds.isEmpty() ? Map.of()
                : vendaCaixaRepository.findAllById(vendaIds).stream()
                        .collect(Collectors.toMap(VendaCaixa::getId, VendaCaixa::getNumero));

        Map<ReferenciaMovimentacaoTipo, Map<UUID, Integer>> numeros = Map.of(
                ReferenciaMovimentacaoTipo.PRODUCAO, numeroProducaoPorId,
                ReferenciaMovimentacaoTipo.ORCAMENTO, numeroOrcamentoPorId,
                ReferenciaMovimentacaoTipo.COMPRA, numeroCompraPorId,
                ReferenciaMovimentacaoTipo.CAIXA, numeroVendaPorId);

        return movimentacoes.map(mov -> insumoMapper.toMovimentacaoResponse(mov, resolverReferencia(mov, numeros)));
    }

    private Set<UUID> referenciaIdsDoTipo(Page<MovimentacaoInsumo> movimentacoes, ReferenciaMovimentacaoTipo tipo) {
        return movimentacoes.getContent().stream()
                .filter(m -> m.getReferenciaTipo() == tipo)
                .map(MovimentacaoInsumo::getReferenciaId)
                .collect(Collectors.toSet());
    }

    /**
     * #542/RN-NOVA-7 (V0.15.0) — referência legível de toda movimentação: PRD-N, ORC-N, COM-N, CX-N.
     * Nunca o ID interno. CAIXA passou a existir em movimentacoes_insumo na V0.13.0 (#516, V54) e o
     * switch antigo lançava IllegalStateException (500 no histórico do insumo) — corrigido aqui.
     */
    private String resolverReferencia(MovimentacaoInsumo mov,
                                      Map<ReferenciaMovimentacaoTipo, Map<UUID, Integer>> numeros) {
        if (mov.getReferenciaTipo() == null) {
            return null;
        }
        String prefixo = switch (mov.getReferenciaTipo()) {
            case PRODUCAO -> "PRD";
            case ORCAMENTO -> "ORC";
            case COMPRA -> "COM";
            case CAIXA -> "CX";
        };
        Integer numero = numeros.get(mov.getReferenciaTipo()).get(mov.getReferenciaId());
        return numero != null ? IdentificadorFormatter.formatar(prefixo, numero) : prefixo + "-?";
    }

    // RN-NOVA-5/DT-NOVA-4 (V0.14.0, #514) — "Edição manual": tipo (ENTRADA/SAIDA) decide a direção
    // do estoque; motivo/observação (INS-007) validados igual para as duas. Checagem de estoque
    // negativo só se aplica à SAIDA — ENTRADA nunca reduz estoque, não há como violar a trava.
    public MovimentacaoInsumoResponseDTO baixaManual(UUID insumoId, BaixaManualInsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        InsumoUtilizavel.exigirNaoRascunho(insumo, "movimentado no estoque");

        BigDecimal estoqueResultante = request.tipo() == TipoMovimentacaoInsumo.ENTRADA
                ? insumo.getEstoqueAtual().add(request.quantidade())
                : insumo.getEstoqueAtual().subtract(request.quantidade());
        if (request.tipo() == TipoMovimentacaoInsumo.SAIDA
                && estoqueResultante.compareTo(BigDecimal.ZERO) < 0 && !insumo.getPermitirEstoqueNegativo()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + insumo.getNome() + ". Este insumo não permite estoque negativo.");
        }

        insumo.setEstoqueAtual(estoqueResultante);
        insumoRepository.save(insumo);

        MovimentacaoInsumo movimentacao = MovimentacaoInsumo.builder()
                .insumo(insumo)
                .tipo(request.tipo())
                .motivo(request.motivo())
                .quantidade(request.quantidade())
                .custoUnitario(insumo.getCustoUnitario())
                .observacao(request.observacao())
                .estornada(false)
                .build();

        return insumoMapper.toMovimentacaoResponse(movimentacaoInsumoRepository.save(movimentacao), null);
    }

    @Transactional(readOnly = true)
    public List<ProdutoRelacionadoResponse> listarProdutosRelacionados(UUID insumoId) {
        UUID usuarioId = getUsuarioIdAutenticado();
        insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        return fichaTecnicaItemRepository.findProdutosByInsumoId(insumoId)
                .stream()
                .map(insumoMapper::toProdutoRelacionadoResponse)
                .toList();
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
