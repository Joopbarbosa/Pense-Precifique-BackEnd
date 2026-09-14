package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.ResolverVinculosInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.SubstituicaoInsumoRequestDTO;
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
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Transactional(readOnly = true)
    public Page<InsumoResponseDTO> listar(String busca, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Pageable pageableOrdenado = PageableOrdenacaoResolver.resolver(pageable, CAMPOS_ORDENACAO_INSUMO,
                "nome, numero, custoUnitario, estoqueAtual, createdAt");

        Page<Insumo> pagina = (busca != null && !busca.isBlank())
                ? insumoRepository.findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
                        usuarioId, busca, pageableOrdenado)
                : insumoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageableOrdenado);

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

        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
                request.nome(), request.marca(), usuarioId)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        Usuario usuario = getUsuarioAutenticado();
        Insumo insumo = insumoMapper.toEntity(request, usuario);
        // #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition.
        usuarioRepository.lockPorId(usuarioId);
        insumo.setNumero(NumeroSequencialUtil.proximoNumero(
                insumoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Insumo::getNumero)));

        // RN-NOVA-1 (V0.10.0, #442, altera INS-003) — cadastro de insumo NÃO gera mais
        // MovimentacaoInsumo/LoteCompra automáticos. Custo unitário é calculado e persistido direto
        // a partir do custo e quantidade informados (mesma fórmula/escala de
        // LoteCompraService#registrarCompraIndividual para o caso trivial de insumo novo, sem estoque
        // anterior a ponderar) — estoqueAtual permanece 0 (default da entidade), sem histórico de
        // movimentação. Entrada de estoque real passa a exigir sempre "Registrar compra" ou "Entrada
        // manual", igual a qualquer entrada subsequente.
        BigDecimal custoUnitario = request.precoTotalCompraInicial()
                .divide(request.quantidadeCompradaInicial(), 6, RoundingMode.HALF_UP);
        insumo.setCustoUnitario(custoUnitario);

        insumo = insumoRepository.save(insumo);

        return insumoMapper.toResponse(insumo);
    }

    public InsumoResponseDTO editar(UUID id, InsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndIdNotAndDeletedAtIsNull(
                request.nome(), request.marca(), usuarioId, id)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        insumoMapper.updateEntity(request, insumo);
        return insumoMapper.toResponse(insumoRepository.save(insumo));
    }

    /** #228/PDT-0XX — DELETE também passa a checar vínculo de ficha técnica, igual a {@link #inativar(UUID)}. */
    public void excluir(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        validarSemVinculoFichaTecnica(insumo);
        insumo.setDeletedAt(LocalDateTime.now());
        insumoRepository.save(insumo);
    }

    /**
     * INS-010 — inativação reversível: {@code ativo=false}, insumo continua existindo (deletedAt
     * permanece null). INS-011 — só é permitida se o insumo não estiver em ficha técnica de nenhum
     * produto não excluído — produto inativado ainda pode voltar a vender, então continua contando
     * como uso.
     */
    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        validarSemVinculoFichaTecnica(insumo);
        insumo.setAtivo(false);
        insumoRepository.save(insumo);
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

        List<Produto> vinculados = fichaTecnicaItemRepository.findProdutosByInsumoId(id);
        if (vinculados.isEmpty()) {
            throw new BusinessException("Insumo " + insumo.getNome() + " não possui vínculos pendentes de resolução.");
        }

        if (request.acao() == AcaoResolucaoVinculo.REMOVER_VINCULOS) {
            vinculados.forEach(produto -> produto.setAtivo(false));
            produtoRepository.saveAll(vinculados);
        } else {
            aplicarSubstituicoes(id, usuarioId, vinculados, request.substituicoes());
        }

        switch (request.operacao()) {
            case INATIVAR -> insumo.setAtivo(false);
            case EXCLUIR -> insumo.setDeletedAt(LocalDateTime.now());
        }
        insumoRepository.save(insumo);
    }

    private void aplicarSubstituicoes(UUID insumoId, UUID usuarioId, List<Produto> vinculados,
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

        return movimentacoes.map(mov -> insumoMapper.toMovimentacaoResponse(
                mov, resolverReferencia(mov, numeroProducaoPorId, numeroOrcamentoPorId)));
    }

    private Set<UUID> referenciaIdsDoTipo(Page<MovimentacaoInsumo> movimentacoes, ReferenciaMovimentacaoTipo tipo) {
        return movimentacoes.getContent().stream()
                .filter(m -> m.getReferenciaTipo() == tipo)
                .map(MovimentacaoInsumo::getReferenciaId)
                .collect(Collectors.toSet());
    }

    private String resolverReferencia(
            MovimentacaoInsumo mov, Map<UUID, Integer> numeroProducaoPorId, Map<UUID, Integer> numeroOrcamentoPorId) {
        if (mov.getReferenciaTipo() == null) {
            return null;
        }
        return switch (mov.getReferenciaTipo()) {
            case LOTE_COMPRA -> "Compra em lote";
            case PRODUCAO -> {
                Integer numero = numeroProducaoPorId.get(mov.getReferenciaId());
                yield numero != null ? IdentificadorFormatter.formatar("PRD", numero) : "PRD-?";
            }
            case ORCAMENTO -> {
                Integer numero = numeroOrcamentoPorId.get(mov.getReferenciaId());
                yield numero != null ? IdentificadorFormatter.formatar("ORC", numero) : "ORC-?";
            }
        };
    }

    public MovimentacaoInsumoResponseDTO baixaManual(UUID insumoId, BaixaManualInsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        BigDecimal estoqueResultante = insumo.getEstoqueAtual().subtract(request.quantidade());
        if (estoqueResultante.compareTo(BigDecimal.ZERO) < 0 && !insumo.getPermitirEstoqueNegativo()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + insumo.getNome() + ". Este insumo não permite estoque negativo.");
        }

        insumo.setEstoqueAtual(estoqueResultante);
        insumoRepository.save(insumo);

        MovimentacaoInsumo movimentacao = MovimentacaoInsumo.builder()
                .insumo(insumo)
                .tipo(TipoMovimentacaoInsumo.SAIDA)
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
