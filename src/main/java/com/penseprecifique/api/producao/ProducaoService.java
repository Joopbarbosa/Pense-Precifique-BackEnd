package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoInsumoConsumido;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoOrigemProducao;
import com.penseprecifique.api.shared.dto.request.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.ConsumoRealRequest;
import com.penseprecifique.api.shared.dto.request.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.FinalizarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.PerdaProducaoRequest;
import com.penseprecifique.api.shared.dto.request.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.request.RetormarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.TravarProducaoRequest;
import com.penseprecifique.api.shared.dto.response.AgruparProducoesResponse;
import com.penseprecifique.api.shared.dto.response.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.DivisaoProducaoResponse;
import com.penseprecifique.api.shared.dto.response.InsumoConsumidoResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ProducaoMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProducaoService {

    private final ProducaoRepository producaoRepository;
    private final ProducaoProdutoRepository producaoProdutoRepository;
    private final HistoricoStatusProducaoRepository historicoStatusProducaoRepository;
    private final ProducaoInsumoConsumidoRepository producaoInsumoConsumidoRepository;
    private final ProdutoRepository produtoRepository;
    private final InsumoRepository insumoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProducaoMapper producaoMapper;

    // #158/RN-NOVA-6 — allowlist explícita dos 4 critérios de ordenação aceitos em GET /producoes.
    // "produto" e "quantidade" são agregados (MIN/SUM sobre producao_produtos, já que uma Producao
    // agrupa N produtos — RN-061) — a expressão JPQL correspondente é resolvida aqui, nunca aceita
    // do cliente. Campo fora da allowlist é rejeitado com BusinessException (nunca ignorado em
    // silêncio nem repassado cru pro Sort, que exporia coluna interna via parâmetro).
    private static final Map<String, String> CAMPOS_ORDENACAO_PRODUCAO = Map.of(
            "dataInicio", "p.dataInicio",
            "estado", "p.estado",
            "produto", "MIN(pp.produto.nome)",
            "quantidade", "SUM(pp.quantidade)"
    );

    /**
     * #184/#192 — RN-NOVA-2: filtro opcional por intervalo de dataInicio, usado tanto pela Listagem
     * quanto pelo Kanban (o front chama o mesmo GET /producoes pros dois — só muda `estado`/`size`
     * do request, confirmado em ListaProducaoPage.tsx: `carregarKanban` e `carregar` chamam o mesmo
     * `producaoService.listar`). Sem os parâmetros, comportamento atual é mantido (sem corte de período).
     */
    @Transactional(readOnly = true)
    public Page<ProducaoResponse> listar(String busca, EstadoProducao estado,
                                          LocalDate dataInicioDe, LocalDate dataInicioAte, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Integer buscaNumero = null;
        String buscaNome = null;
        if (busca != null && !busca.isBlank()) {
            String normalizado = busca.trim();
            String semPrefixo = normalizado.toUpperCase().startsWith("PRD-") ? normalizado.substring(4) : normalizado;
            if (semPrefixo.matches("\\d+")) {
                buscaNumero = Integer.valueOf(semPrefixo);
            } else {
                buscaNome = normalizado;
            }
        }

        Pageable pageableOrdenado = resolverPageableOrdenado(pageable);
        Page<UUID> idsPage = producaoRepository.buscarIdsOrdenados(
                usuarioId, estado, dataInicioDe, dataInicioAte, buscaNumero, buscaNome, pageableOrdenado);

        List<UUID> ids = idsPage.getContent();
        Map<UUID, Producao> porId = producaoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producao::getId, p -> p));
        List<ProducaoResponse> conteudo = ids.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .map(this::montarResponseComAlertas)
                .toList();

        return new PageImpl<>(conteudo, pageable, idsPage.getTotalElements());
    }

    /**
     * Traduz o Sort recebido (nomes de campo voltados pro cliente) na expressão JPQL real, validando
     * contra a allowlist. Sem Sort informado → default numero DESC (produção mais recente primeiro).
     */
    private Pageable resolverPageableOrdenado(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            Sort padrao = JpaSort.unsafe(Sort.Direction.DESC, "p.numero");
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), padrao);
        }

        Sort sortResolvido = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String expressao = CAMPOS_ORDENACAO_PRODUCAO.get(order.getProperty());
            if (expressao == null) {
                throw new BusinessException("Campo de ordenação inválido: '" + order.getProperty()
                        + "'. Permitidos: dataInicio, estado, produto, quantidade.");
            }
            sortResolvido = sortResolvido.and(JpaSort.unsafe(order.getDirection(), expressao));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortResolvido);
    }

    @Transactional(readOnly = true)
    public ProducaoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));
        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        return montarDetalhe(producao, calcularAlertasAoVivo(produtos));
    }

    /**
     * #123 — alertasInsumos recalculado ao vivo em GET /producoes e GET /producoes/{id}, mesmo cálculo de RN-064.
     * #156 — historicoStatus também exposto aqui (mesma fonte de ProducaoDetalheResponse), pro front distinguir
     * TRAVADA_USUARIO/TRAVADA_SISTEMA sem precisar abrir o detalhe.
     */
    private ProducaoResponse montarResponseComAlertas(Producao producao) {
        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        List<HistoricoStatusProducao> historico = historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producao.getId());
        return producaoMapper.toResponse(producao, produtos, calcularAlertasAoVivo(produtos), historico);
    }

    @Transactional(readOnly = true)
    public List<InsumoConsumidoResponse> previewInsumosConsumidos(UUID produtoId, BigDecimal quantidade) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        List<FichaTecnicaItem> ficha = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        List<InsumoConsumidoResponse> preview = new ArrayList<>();
        for (FichaTecnicaItem item : ficha) {
            preview.add(montarPreview(item, quantidade));
        }
        return preview;
    }

    /**
     * RN-NOVA-7 — simulação de alertas ao adicionar produto na tela de Nova Produção: recalcula o
     * consumo acumulado (produtos já na lista + o novo) sem persistir nada, reaproveitando as mesmas
     * validarEResolverProdutos()/calcularAlertas() usadas em criarProducao()/editarProducao(). Situação
     * SUFICIENTE também é retornada aqui (não filtrada), mesmo comportamento hoje de calcularAlertas()
     * na confirmação final — quem decide omitir da exibição é o front, não o backend.
     */
    @Transactional(readOnly = true)
    public List<AlertaInsumoResponse> simularAlertas(List<ProducaoProdutoRequest> produtos) {
        UUID usuarioId = getUsuarioIdAutenticado();
        ProdutosValidados validados = validarEResolverProdutos(produtos, usuarioId);
        return calcularAlertas(validados);
    }

    /** RN-061/062/064/077 — cria produção com N produtos, sem movimentação de estoque. Nasce AGUARDANDO_INICIO. */
    public ProducaoDetalheResponse criarProducao(CriarProducaoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        LocalDate dataInicio = request.getDataInicio() != null ? request.getDataInicio() : LocalDate.now();
        validarDatas(dataInicio, request.getDataTerminoPrevista());

        ProdutosValidados validados = validarEResolverProdutos(request.getProdutos(), usuarioId);

        Producao producao = Producao.builder()
                .usuario(usuario)
                .numero(proximoNumero(usuarioId))
                .estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(dataInicio)
                .dataTerminoPrevista(request.getDataTerminoPrevista())
                .observacoes(request.getObservacoes())
                .build();
        producao = producaoRepository.save(producao);

        List<ProducaoProduto> produtosGravados = gravarProducaoProdutos(producao, validados);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(null)
                .statusNovo(EstadoProducao.AGUARDANDO_INICIO)
                .origem(OrigemHistoricoStatus.USUARIO)
                .build());

        List<AlertaInsumoResponse> alertas = calcularAlertas(validados);
        return montarDetalhe(producao, alertas);
    }

    /** RN-063 — edição restrita a AGUARDANDO_INICIO; substitui a lista de produtos por completo. */
    public ProducaoDetalheResponse editarProducao(UUID id, CriarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Apenas produções aguardando início podem ser editadas");
        }

        LocalDate dataInicio = request.getDataInicio() != null ? request.getDataInicio() : producao.getDataInicio();
        validarDatas(dataInicio, request.getDataTerminoPrevista());

        ProdutosValidados validados = validarEResolverProdutos(request.getProdutos(), usuarioId);

        producaoProdutoRepository.deleteAll(producaoProdutoRepository.findByProducaoId(producao.getId()));

        producao.setDataInicio(dataInicio);
        producao.setDataTerminoPrevista(request.getDataTerminoPrevista());
        producao.setObservacoes(request.getObservacoes());
        producao = producaoRepository.save(producao);

        List<ProducaoProduto> produtosGravados = gravarProducaoProdutos(producao, validados);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(EstadoProducao.AGUARDANDO_INICIO)
                .statusNovo(EstadoProducao.AGUARDANDO_INICIO)
                .origem(OrigemHistoricoStatus.USUARIO)
                .justificativa("Edição")
                .build());

        List<AlertaInsumoResponse> alertas = calcularAlertas(validados);
        return montarDetalhe(producao, alertas);
    }

    /**
     * RN-071/072 — cancela produção do ciclo de vida novo. AGUARDANDO_INICIO nunca moveu estoque
     * (Fluxo A, simples). EM_ANDAMENTO/TRAVADA já baixaram insumos no iniciar() (Fluxo B) — exige
     * declaração de quanto foi realmente consumido e estorna a diferença por componente.
     */
    public ProducaoDetalheResponse cancelar(UUID id, CancelarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        EstadoProducao estadoAtual = producao.getEstado();
        if (estadoAtual == EstadoProducao.FINALIZADA) {
            throw new BusinessException("Produções finalizadas não podem ser canceladas");
        }
        if (estadoAtual == EstadoProducao.CANCELADA) {
            throw new BusinessException("Esta produção já está cancelada");
        }
        if (estadoAtual == EstadoProducao.NAO_REALIZADA) {
            throw new BusinessException("Produções não realizadas não podem ser canceladas");
        }

        if (estadoAtual == EstadoProducao.AGUARDANDO_INICIO) {
            producao.setJustificativaCancelamento(request.getJustificativa());
            transicionar(producao, EstadoProducao.CANCELADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
            return montarDetalhe(producao, List.of());
        }

        // EM_ANDAMENTO ou TRAVADA — insumos já baixados no iniciar(), exige declaração de consumo real.
        aplicarConsumoReal(producao, request.getConsumoReal(), request.getJustificativa());

        producao.setJustificativaCancelamento(request.getJustificativa());
        transicionar(producao, EstadoProducao.CANCELADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
        return montarDetalhe(producao, List.of());
    }

    /**
     * RN-072 — aplica a declaração de consumo real de uma produção com insumos já baixados
     * (EM_ANDAMENTO/TRAVADA), estornando a diferença por componente. Extraído de cancelar() para reuso
     * em agrupar() (RN-074), que precisa da mesma lógica para as produções originais já em andamento.
     */
    private void aplicarConsumoReal(Producao producao, List<ConsumoRealRequest> consumoReal, String justificativa) {
        Map<UUID, ConsumoRealRequest> declarado = new LinkedHashMap<>();
        if (consumoReal != null) {
            for (ConsumoRealRequest item : consumoReal) {
                UUID componenteId = item.getInsumoId() != null ? item.getInsumoId() : item.getProdutoBaseId();
                declarado.put(componenteId, item);
            }
        }

        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        for (ProducaoInsumoConsumido consumido : consumidos) {
            boolean isInsumo = consumido.getInsumo() != null;
            UUID componenteId = isInsumo ? consumido.getInsumo().getId() : consumido.getProdutoBase().getId();
            String nome = isInsumo ? consumido.getInsumo().getNome() : consumido.getProdutoBase().getNome();
            BigDecimal original = consumido.getQuantidade();

            ConsumoRealRequest item = declarado.get(componenteId);
            // Sem declaração para este componente — assume consumo total, nenhum estorno (comportamento
            // do fluxo antigo, que sempre estornava tudo quando não havia como saber o consumo real).
            BigDecimal consumidoReal = item != null ? item.getQuantidadeConsumida() : original;

            if (consumidoReal.compareTo(original) > 0) {
                throw new BusinessException("Quantidade consumida de " + nome
                        + " não pode ser maior que a quantidade baixada originalmente (" + original + ")");
            }

            BigDecimal diferenca = original.subtract(consumidoReal);
            if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
                boolean totalmenteEstornado = consumidoReal.compareTo(BigDecimal.ZERO) == 0;
                estornarComponente(producao, consumido, diferenca, totalmenteEstornado, justificativa);
            }
        }
    }

    /**
     * Estorna a diferença não consumida de um componente. `estornada` na MovimentacaoInsumo/Produto
     * original só é marcada quando o estorno é total — o campo é booleano simples, sem noção de
     * "parcialmente estornada", então marcar em estorno parcial ficaria um dado incorreto.
     */
    private void estornarComponente(Producao producao, ProducaoInsumoConsumido consumido, BigDecimal diferenca,
                                      boolean totalmenteEstornado, String justificativa) {
        if (consumido.getInsumo() != null) {
            Insumo insumo = consumido.getInsumo();
            insumo.setEstoqueAtual(insumo.getEstoqueAtual().add(diferenca));
            insumoRepository.save(insumo);

            if (totalmenteEstornado) {
                movimentacaoInsumoRepository
                        .findByInsumoIdAndMotivoAndReferenciaId(insumo.getId(), MotivoMovimentacaoInsumo.PRODUCAO, producao.getId())
                        .ifPresent(original -> {
                            original.setEstornada(true);
                            movimentacaoInsumoRepository.save(original);
                        });
            }

            movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                    .insumo(insumo)
                    .tipo(TipoMovimentacaoInsumo.ENTRADA)
                    .motivo(MotivoMovimentacaoInsumo.ESTORNO_PRODUCAO)
                    .quantidade(diferenca)
                    .observacao(justificativa)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO)
                    .estornada(false)
                    .build());

        } else if (consumido.getProdutoBase() != null) {
            Produto base = consumido.getProdutoBase();
            base.setEstoqueAtual(base.getEstoqueAtual().add(diferenca));
            produtoRepository.save(base);

            if (totalmenteEstornado) {
                movimentacaoProdutoRepository
                        .findByProdutoIdAndMotivoAndReferenciaIdAndTipo(
                                base.getId(), MotivoMovimentacaoProduto.PRODUCAO, producao.getId(), TipoMovimentacaoProduto.SAIDA)
                        .ifPresent(original -> {
                            original.setEstornada(true);
                            movimentacaoProdutoRepository.save(original);
                        });
            }

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(base)
                    .tipo(TipoMovimentacaoProduto.ENTRADA)
                    .motivo(MotivoMovimentacaoProduto.ESTORNO_PRODUCAO)
                    .quantidade(diferenca)
                    .observacao(justificativa)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                    .estornada(false)
                    .build());
        }
    }

    /** RN-065/066/067 — inicia produção: sem bloqueante, baixa insumos e vai para EM_ANDAMENTO;
     *  com bloqueante, trava automaticamente (SISTEMA) sem baixar nada, a menos que request.dividir=true. */
    public Object iniciar(UUID id, IniciarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Apenas produções aguardando início podem ser iniciadas");
        }

        List<ProducaoProduto> produtosDaProducao = producaoProdutoRepository.findByProducaoId(producao.getId());
        List<UUID> confirmados = request.getConfirmarEstoqueNegativoInsumoIds() != null
                ? request.getConfirmarEstoqueNegativoInsumoIds() : List.of();
        VerificacaoInsumos verificacao = verificarEBaixarSeLiberado(producao, produtosDaProducao, confirmados);

        if (!verificacao.bloqueantes().isEmpty()) {
            if (Boolean.TRUE.equals(request.getDividir())) {
                return dividir(producao, produtosDaProducao, verificacao, confirmados);
            }
            transicionar(producao, EstadoProducao.TRAVADA, OrigemHistoricoStatus.SISTEMA,
                    "Insumo(s) bloqueante(s): " + String.join(", ", verificacao.bloqueantes()));
            return montarDetalhe(producao, List.of());
        }

        // RN-052 — componente com estoque negativo permitido e ainda não confirmado: nada foi baixado,
        // devolve o aviso para o usuário confirmar antes de reenviar.
        if (!verificacao.avisosPendentes().isEmpty()) {
            return montarConfirmacaoEstoqueNegativo(verificacao);
        }

        transicionar(producao, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, null);
        return montarDetalhe(producao, List.of());
    }

    /** Trava manual — só a partir de EM_ANDAMENTO, sem tocar estoque (insumos já baixados permanecem). */
    public ProducaoDetalheResponse travar(UUID id, TravarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.EM_ANDAMENTO) {
            throw new BusinessException("Apenas produções em andamento podem ser travadas manualmente");
        }

        transicionar(producao, EstadoProducao.TRAVADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
        return montarDetalhe(producao, List.of());
    }

    /**
     * RN-069 — retoma produção travada. Duas origens possíveis de TRAVADA exigem tratamento diferente:
     * (a) trava veio do próprio iniciar() bloqueando — nenhum insumo foi baixado ainda, então reverifica
     *     e, se liberado, baixa pela primeira vez; (b) trava veio de travar() manual após iniciar() já ter
     *     baixado — não há o que reverificar nem baixar de novo (dobraria o consumo), só volta o estado.
     */
    public Object retomar(UUID id, RetormarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.TRAVADA) {
            throw new BusinessException("Apenas produções travadas podem ser retomadas");
        }

        List<ProducaoInsumoConsumido> jaConsumido = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        if (!jaConsumido.isEmpty()) {
            transicionar(producao, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, null);
            return montarDetalhe(producao, List.of());
        }

        List<ProducaoProduto> produtosDaProducao = producaoProdutoRepository.findByProducaoId(producao.getId());
        List<UUID> confirmados = (request != null && request.getConfirmarEstoqueNegativoInsumoIds() != null)
                ? request.getConfirmarEstoqueNegativoInsumoIds() : List.of();
        VerificacaoInsumos verificacao = verificarEBaixarSeLiberado(producao, produtosDaProducao, confirmados);

        if (!verificacao.bloqueantes().isEmpty()) {
            if (request != null && Boolean.TRUE.equals(request.getDividir())) {
                return dividir(producao, produtosDaProducao, verificacao, confirmados);
            }
            // Permanece TRAVADA — tentativa sem sucesso não é uma transição de estado, sem histórico novo.
            return montarDetalhe(producao, List.of());
        }

        // RN-052 — componente com estoque negativo permitido e ainda não confirmado: nada foi baixado,
        // devolve o aviso para o usuário confirmar antes de reenviar.
        if (!verificacao.avisosPendentes().isEmpty()) {
            return montarConfirmacaoEstoqueNegativo(verificacao);
        }

        transicionar(producao, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, null);
        return montarDetalhe(producao, List.of());
    }

    /** Produtos da produção classificados conforme algum componente da ficha técnica está entre os bloqueantes. */
    private record ProdutosDivididos(List<ProducaoProduto> semBloqueio, List<ProducaoProduto> comBloqueio) {
    }

    private ProdutosDivididos classificarPorBloqueio(List<ProducaoProduto> produtosDaProducao, List<String> nomesBloqueantes) {
        List<ProducaoProduto> semBloqueio = new ArrayList<>();
        List<ProducaoProduto> comBloqueio = new ArrayList<>();
        for (ProducaoProduto producaoProduto : produtosDaProducao) {
            boolean bloqueado = fichaTecnicaItemRepository.findByProdutoId(producaoProduto.getProduto().getId()).stream()
                    .anyMatch(item -> nomesBloqueantes.contains(nomeComponente(item)));
            (bloqueado ? comBloqueio : semBloqueio).add(producaoProduto);
        }
        return new ProdutosDivididos(semBloqueio, comBloqueio);
    }

    private String nomeComponente(FichaTecnicaItem item) {
        if (item.getInsumo() != null) {
            return item.getInsumo().getNome();
        }
        return item.getProdutoBase() != null ? item.getProdutoBase().getNome() : null;
    }

    /**
     * RN-065 — divide a produção quando iniciar()/retomar() encontram bloqueante e o usuário optou por
     * dividir em vez de travar tudo. Produtos sem bloqueio formam uma nova produção que já baixa insumos
     * e vai para EM_ANDAMENTO; produtos com bloqueio formam outra, TRAVADA, sem baixar nada; a produção
     * original vira NÃO_REALIZADA, substituída pelas duas novas. RN-052 — componente de producaoA com
     * estoque negativo permitido e ainda não confirmado interrompe a divisão inteira antes de qualquer
     * gravação (nenhuma produção nova é criada, original não transiciona) até o usuário confirmar.
     */
    private Object dividir(Producao producaoOriginal, List<ProducaoProduto> produtosDaProducao,
                            VerificacaoInsumos verificacao, List<UUID> idsConfirmados) {
        ProdutosDivididos divididos = classificarPorBloqueio(produtosDaProducao, verificacao.bloqueantes());

        if (divididos.semBloqueio().isEmpty()) {
            throw new BusinessException("Não é possível dividir — todos os produtos têm insumos bloqueantes. "
                    + "Opções: resolver o estoque ou cancelar a produção.");
        }

        VerificacaoInsumos verificacaoA = verificarComponentes(divididos.semBloqueio());
        List<AvisoEstoqueNegativoResponse> avisosA = avisosNaoConfirmados(verificacaoA.componentes(), idsConfirmados);
        if (!avisosA.isEmpty()) {
            return montarConfirmacaoEstoqueNegativo(
                    new VerificacaoInsumos(verificacaoA.componentes(), verificacaoA.bloqueantes(), avisosA));
        }

        String identificadorOriginal = IdentificadorFormatter.formatar("PRD", producaoOriginal.getNumero());

        Producao producaoA = criarProducaoFilha(producaoOriginal, divididos.semBloqueio(), TipoOrigemProducao.DIVISAO);
        for (ComponenteNecessidade componente : verificacaoA.componentes()) {
            baixarComponente(producaoA, componente);
        }
        registrarNascimento(producaoA, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.SISTEMA,
                "Criada por divisão de " + identificadorOriginal);

        Producao producaoB = criarProducaoFilha(producaoOriginal, divididos.comBloqueio(), TipoOrigemProducao.DIVISAO);
        registrarNascimento(producaoB, EstadoProducao.TRAVADA, OrigemHistoricoStatus.SISTEMA,
                "Criada por divisão de " + identificadorOriginal + ". Insumo(s) bloqueante(s): "
                        + String.join(", ", verificacao.bloqueantes()));

        String justificativaOriginal = "Substituída pelas produções " + IdentificadorFormatter.formatar("PRD", producaoA.getNumero())
                + " e " + IdentificadorFormatter.formatar("PRD", producaoB.getNumero()) + " por insumo bloqueante";
        producaoOriginal.setJustificativaNaoRealizada(justificativaOriginal);
        transicionar(producaoOriginal, EstadoProducao.NAO_REALIZADA, OrigemHistoricoStatus.SISTEMA, justificativaOriginal);

        DivisaoProducaoResponse response = new DivisaoProducaoResponse();
        response.setProducaoOriginal(montarDetalhe(producaoOriginal, List.of()));
        response.setProducaoA(montarDetalhe(producaoA, List.of()));
        response.setProducaoB(montarDetalhe(producaoB, List.of()));
        return response;
    }

    /** Cria uma produção filha (DIVISAO/AGRUPAMENTO) copiando dataInicio/dataTerminoPrevista/observacoes
     *  da origem e gravando os ProducaoProduto informados. Nasce sem histórico — quem chama transiciona
     *  via registrarNascimento()/transicionar(). */
    private Producao criarProducaoFilha(Producao origem, List<ProducaoProduto> produtosOrigem, TipoOrigemProducao tipo) {
        Producao filha = Producao.builder()
                .usuario(origem.getUsuario())
                .numero(proximoNumero(origem.getUsuario().getId()))
                .estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(origem.getDataInicio())
                .dataTerminoPrevista(origem.getDataTerminoPrevista())
                .observacoes(origem.getObservacoes())
                .producaoOrigem(origem)
                .tipoOrigem(tipo)
                .build();
        filha = producaoRepository.save(filha);

        for (ProducaoProduto producaoProduto : produtosOrigem) {
            producaoProdutoRepository.save(ProducaoProduto.builder()
                    .producao(filha)
                    .produto(producaoProduto.getProduto())
                    .quantidade(producaoProduto.getQuantidade())
                    .build());
        }
        return filha;
    }

    /** Registra o "nascimento" de uma produção filha diretamente em um estado — statusAnterior null,
     *  mesmo padrão usado em criarProducao() para a produção raiz (nunca houve um estado anterior real). */
    private void registrarNascimento(Producao producao, EstadoProducao estado, OrigemHistoricoStatus origem, String justificativa) {
        producao.setEstado(estado);
        producaoRepository.save(producao);
        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(null)
                .statusNovo(estado)
                .origem(origem)
                .justificativa(justificativa)
                .build());
    }

    /**
     * RN-070/#188/RN-NOVA-4 — finaliza produção: incrementa estoque de cada produto por
     * (quantidade planejada − perda declarada), registra a entrada com a quantidade real (não a
     * planejada, quando há perda — Opção A, sem registro paralelo), preenche dataTerminoReal e vai
     * para FINALIZADA (imutável, sem saída). `request`/`perdas` são opcionais — produto ausente da
     * lista mantém o comportamento anterior (perda 0, incrementa o total planejado).
     */
    public ProducaoDetalheResponse finalizar(UUID id, FinalizarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.EM_ANDAMENTO) {
            throw new BusinessException("Apenas produções em andamento podem ser finalizadas");
        }

        Map<UUID, BigDecimal> perdasPorProduto = new LinkedHashMap<>();
        if (request != null && request.getPerdas() != null) {
            for (PerdaProducaoRequest perda : request.getPerdas()) {
                perdasPorProduto.put(perda.getProdutoId(), perda.getQuantidadePerdida());
            }
        }

        List<ProducaoProduto> produtosDaProducao = producaoProdutoRepository.findByProducaoId(producao.getId());
        for (ProducaoProduto producaoProduto : produtosDaProducao) {
            Produto produto = producaoProduto.getProduto();
            BigDecimal planejada = producaoProduto.getQuantidade();
            BigDecimal perda = perdasPorProduto.getOrDefault(produto.getId(), BigDecimal.ZERO);

            if (perda.compareTo(planejada) > 0) {
                throw new BusinessException("Quantidade perdida de " + produto.getNome() + " (" + perda
                        + ") não pode ser maior que a quantidade planejada (" + planejada + ")");
            }

            producaoProduto.setQuantidadePerdida(perda);
            producaoProdutoRepository.save(producaoProduto);

            BigDecimal quantidadeReal = planejada.subtract(perda);
            if (quantidadeReal.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            produto.setEstoqueAtual(produto.getEstoqueAtual().add(quantidadeReal));
            produtoRepository.save(produto);

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(produto)
                    .tipo(TipoMovimentacaoProduto.ENTRADA)
                    .motivo(MotivoMovimentacaoProduto.PRODUCAO)
                    .quantidade(quantidadeReal)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                    .estornada(false)
                    .build());
        }

        producao.setDataTerminoReal(LocalDate.now());
        transicionar(producao, EstadoProducao.FINALIZADA, OrigemHistoricoStatus.USUARIO, null);
        return montarDetalhe(producao, List.of());
    }

    /**
     * RN-074 — agrupa 2+ produções em uma nova. Produções com insumos já baixados (EM_ANDAMENTO/TRAVADA)
     * exigem declaração de consumo real antes de sair de cena (mesma lógica de cancelar() — RN-072). Os
     * produtos das originais são consolidados por produto (RN-061). As originais viram NÃO_REALIZADA.
     * RN-052 — componente da nova produção com estoque negativo permitido e ainda não confirmado
     * interrompe o agrupamento inteiro antes de qualquer gravação (nenhum estorno de consumo real,
     * nenhuma produção nova, nenhuma original transicionada) até o usuário confirmar.
     */
    public Object agrupar(AgruparProducoesRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        if (request.getEstadoDestino() != EstadoProducao.AGUARDANDO_INICIO
                && request.getEstadoDestino() != EstadoProducao.EM_ANDAMENTO
                && request.getEstadoDestino() != EstadoProducao.TRAVADA) {
            throw new BusinessException("estadoDestino inválido para agrupamento");
        }

        List<Producao> originais = new ArrayList<>();
        for (UUID producaoId : request.getProducaoIds()) {
            Producao producao = producaoRepository.findByIdAndUsuarioId(producaoId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

            EstadoProducao estado = producao.getEstado();
            if (estado == EstadoProducao.FINALIZADA || estado == EstadoProducao.CANCELADA || estado == EstadoProducao.NAO_REALIZADA) {
                throw new BusinessException("Produção " + IdentificadorFormatter.formatar("PRD", producao.getNumero())
                        + " não pode ser agrupada (status: " + estado + ")");
            }
            originais.add(producao);
        }

        // Consolida os produtos de todas as produções, somando quantidades por produto (RN-061) — calculado
        // antes do Passo 1 para permitir o pré-check de RN-052 sem efeito colateral algum ainda gravado.
        List<ProducaoProduto> produtosConsolidados = consolidarProdutos(originais);

        if (request.getEstadoDestino() == EstadoProducao.EM_ANDAMENTO) {
            List<UUID> confirmados = request.getConfirmarEstoqueNegativoInsumoIds() != null
                    ? request.getConfirmarEstoqueNegativoInsumoIds() : List.of();
            VerificacaoInsumos preCheck = verificarComponentes(produtosConsolidados);
            if (preCheck.bloqueantes().isEmpty()) {
                List<AvisoEstoqueNegativoResponse> avisos = avisosNaoConfirmados(preCheck.componentes(), confirmados);
                if (!avisos.isEmpty()) {
                    return montarConfirmacaoEstoqueNegativo(
                            new VerificacaoInsumos(preCheck.componentes(), preCheck.bloqueantes(), avisos));
                }
            }
        }

        // Passo 1 — produções com insumos já baixados exigem declaração de consumo real antes de sair de cena.
        for (Producao producao : originais) {
            if (producao.getEstado() == EstadoProducao.EM_ANDAMENTO || producao.getEstado() == EstadoProducao.TRAVADA) {
                List<ConsumoRealRequest> consumoReal = request.getConsumoRealPorProducao() != null
                        ? request.getConsumoRealPorProducao().get(producao.getId()) : null;
                aplicarConsumoReal(producao, consumoReal, request.getJustificativa());
            }
        }

        Producao maisRecente = originais.stream()
                .max(Comparator.comparing(Producao::getDataInicio, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow();
        LocalDate dataInicio = request.getDataInicio() != null ? request.getDataInicio() : maisRecente.getDataInicio();
        LocalDate dataTerminoPrevista = request.getDataTerminoPrevista() != null
                ? request.getDataTerminoPrevista() : maisRecente.getDataTerminoPrevista();

        Producao primeira = originais.get(0);
        Producao nova = Producao.builder()
                .usuario(primeira.getUsuario())
                .numero(proximoNumero(usuarioId))
                .estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(dataInicio)
                .dataTerminoPrevista(dataTerminoPrevista)
                .producaoOrigem(primeira)
                .tipoOrigem(TipoOrigemProducao.AGRUPAMENTO)
                .build();
        nova = producaoRepository.save(nova);

        for (ProducaoProduto consolidado : produtosConsolidados) {
            producaoProdutoRepository.save(ProducaoProduto.builder()
                    .producao(nova)
                    .produto(consolidado.getProduto())
                    .quantidade(consolidado.getQuantidade())
                    .build());
        }

        // Passo 3 — leva a nova produção até o estado de destino.
        if (request.getEstadoDestino() == EstadoProducao.AGUARDANDO_INICIO) {
            registrarNascimento(nova, EstadoProducao.AGUARDANDO_INICIO, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
        } else if (request.getEstadoDestino() == EstadoProducao.EM_ANDAMENTO) {
            List<ProducaoProduto> produtosNova = producaoProdutoRepository.findByProducaoId(nova.getId());
            VerificacaoInsumos verificacao = verificarEBaixarSeLiberado(nova, produtosNova);
            if (!verificacao.bloqueantes().isEmpty()) {
                registrarNascimento(nova, EstadoProducao.TRAVADA, OrigemHistoricoStatus.SISTEMA,
                        "Insumo(s) bloqueante(s): " + String.join(", ", verificacao.bloqueantes()));
            } else {
                registrarNascimento(nova, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
            }
        } else {
            registrarNascimento(nova, EstadoProducao.TRAVADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
        }

        // Passo 4 — as originais viram NÃO_REALIZADA, substituídas pela nova.
        String identificadorNova = IdentificadorFormatter.formatar("PRD", nova.getNumero());
        List<ProducaoDetalheResponse> originaisResponse = new ArrayList<>();
        for (Producao producao : originais) {
            String justificativaNaoRealizada = "Agrupada na produção " + identificadorNova;
            producao.setJustificativaNaoRealizada(justificativaNaoRealizada);
            transicionar(producao, EstadoProducao.NAO_REALIZADA, OrigemHistoricoStatus.SISTEMA, justificativaNaoRealizada);
            originaisResponse.add(montarDetalhe(producao, List.of()));
        }

        AgruparProducoesResponse response = new AgruparProducoesResponse();
        response.setProducaoNova(montarDetalhe(nova, List.of()));
        response.setProducoesOriginais(originaisResponse);
        return response;
    }

    /** RN-061 — soma as quantidades do mesmo produto entre todas as produções de origem. Retorna
     *  ProducaoProduto transientes (produto+quantidade, sem id/producao) — usados tanto para o pré-check
     *  de RN-052 quanto para gravar os ProducaoProduto reais da nova produção. */
    private List<ProducaoProduto> consolidarProdutos(List<Producao> originais) {
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();
        Map<UUID, BigDecimal> quantidadesPorProduto = new LinkedHashMap<>();
        for (Producao producao : originais) {
            for (ProducaoProduto producaoProduto : producaoProdutoRepository.findByProducaoId(producao.getId())) {
                UUID produtoId = producaoProduto.getProduto().getId();
                produtosPorId.putIfAbsent(produtoId, producaoProduto.getProduto());
                quantidadesPorProduto.merge(produtoId, producaoProduto.getQuantidade(), BigDecimal::add);
            }
        }
        return produtosPorId.values().stream()
                .map(produto -> ProducaoProduto.builder()
                        .produto(produto)
                        .quantidade(quantidadesPorProduto.get(produto.getId()))
                        .build())
                .toList();
    }

    private void transicionar(Producao producao, EstadoProducao novoEstado, OrigemHistoricoStatus origem, String justificativa) {
        EstadoProducao estadoAnterior = producao.getEstado();
        producao.setEstado(novoEstado);
        producaoRepository.save(producao);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(estadoAnterior)
                .statusNovo(novoEstado)
                .origem(origem)
                .justificativa(justificativa)
                .build());
    }

    private ProducaoDetalheResponse montarDetalhe(Producao producao, List<AlertaInsumoResponse> alertasInsumos) {
        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        List<HistoricoStatusProducao> historico = historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producao.getId());
        List<Producao> producoesFilhas = producaoRepository.findByProducaoOrigemId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos, produtos, alertasInsumos, historico, producoesFilhas);
    }

    /** Componente (insumo ou produto-base) com a necessidade já somada entre todos os produtos da produção. */
    private record ComponenteNecessidade(String nome, BigDecimal necessaria, BigDecimal estoqueAtual,
                                          Boolean permitirEstoqueNegativo, Insumo insumo, Produto produtoBase) {
    }

    /** avisosPendentes (RN-052) só é preenchido por verificarEBaixarSeLiberado(...,idsConfirmados) — vazio
     *  nos demais casos (verificarComponentes puro, ou gate não aplicável ao chamador). */
    private record VerificacaoInsumos(List<ComponenteNecessidade> componentes, List<String> bloqueantes,
                                       List<AvisoEstoqueNegativoResponse> avisosPendentes) {
    }

    /**
     * RN-065 — necessidade de cada componente (insumo ou produto-base) da ficha técnica de cada produto
     * da produção, somada quando o mesmo componente aparece em mais de um produto. RN-059 — componente
     * com permitirEstoqueNegativo=false bloqueia incondicionalmente, nunca contornável por confirmação
     * do request (mesma regra do fluxo antigo removido no P002).
     */
    private VerificacaoInsumos verificarComponentes(List<ProducaoProduto> produtosDaProducao) {
        Map<UUID, BigDecimal> necessidadePorComponente = new LinkedHashMap<>();
        Map<UUID, Insumo> insumosPorId = new LinkedHashMap<>();
        Map<UUID, Produto> produtosBasePorId = new LinkedHashMap<>();

        for (ProducaoProduto producaoProduto : produtosDaProducao) {
            Produto produto = producaoProduto.getProduto();
            exigirRendimentoValido(produto);
            BigDecimal ratioLote = producaoProduto.getQuantidade().divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

            for (FichaTecnicaItem item : fichaTecnicaItemRepository.findByProdutoId(produto.getId())) {
                BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
                if (item.getInsumo() != null) {
                    necessidadePorComponente.merge(item.getInsumo().getId(), necessaria, BigDecimal::add);
                    insumosPorId.putIfAbsent(item.getInsumo().getId(), item.getInsumo());
                } else if (item.getProdutoBase() != null) {
                    necessidadePorComponente.merge(item.getProdutoBase().getId(), necessaria, BigDecimal::add);
                    produtosBasePorId.putIfAbsent(item.getProdutoBase().getId(), item.getProdutoBase());
                }
            }
        }

        List<ComponenteNecessidade> componentes = new ArrayList<>();
        List<String> bloqueantes = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : necessidadePorComponente.entrySet()) {
            Insumo insumo = insumosPorId.get(entry.getKey());
            Produto produtoBase = produtosBasePorId.get(entry.getKey());

            String nome = insumo != null ? insumo.getNome() : produtoBase.getNome();
            BigDecimal estoqueAtual = insumo != null ? insumo.getEstoqueAtual() : produtoBase.getEstoqueAtual();
            Boolean permitirEstoqueNegativo = insumo != null
                    ? insumo.getPermitirEstoqueNegativo() : produtoBase.getPermitirEstoqueNegativo();
            BigDecimal necessaria = entry.getValue();

            componentes.add(new ComponenteNecessidade(nome, necessaria, estoqueAtual, permitirEstoqueNegativo, insumo, produtoBase));

            if (estoqueAtual.subtract(necessaria).compareTo(BigDecimal.ZERO) < 0
                    && Boolean.FALSE.equals(permitirEstoqueNegativo)) {
                bloqueantes.add(nome);
            }
        }

        return new VerificacaoInsumos(componentes, bloqueantes, List.of());
    }

    /** #178 — ponto único usado por iniciar()/retomar()/dividir()/agrupar(): verifica os componentes e,
     *  se nada bloquear, baixa todos direto. Usada por dividir()/agrupar(), onde não há um usuário decidindo
     *  a confirmação de estoque negativo na hora — RN-052 não se aplica, mesmo comportamento de sempre. */
    private VerificacaoInsumos verificarEBaixarSeLiberado(Producao producao, List<ProducaoProduto> produtosDaProducao) {
        VerificacaoInsumos verificacao = verificarComponentes(produtosDaProducao);
        if (verificacao.bloqueantes().isEmpty()) {
            for (ComponenteNecessidade componente : verificacao.componentes()) {
                baixarComponente(producao, componente);
            }
        }
        return verificacao;
    }

    /** RN-052 — variante usada por iniciar()/retomar(), onde a baixa é uma ação direta do usuário: componente
     *  com estoque negativo permitido (permitirEstoqueNegativo=true) e id fora de idsConfirmados vira aviso
     *  pendente em vez de ser baixado — nada é baixado enquanto houver algum aviso pendente. */
    private VerificacaoInsumos verificarEBaixarSeLiberado(Producao producao, List<ProducaoProduto> produtosDaProducao,
                                                            List<UUID> idsConfirmados) {
        VerificacaoInsumos verificacao = verificarComponentes(produtosDaProducao);
        if (!verificacao.bloqueantes().isEmpty()) {
            return verificacao;
        }

        List<AvisoEstoqueNegativoResponse> avisos = avisosNaoConfirmados(verificacao.componentes(), idsConfirmados);
        if (!avisos.isEmpty()) {
            return new VerificacaoInsumos(verificacao.componentes(), verificacao.bloqueantes(), avisos);
        }

        for (ComponenteNecessidade componente : verificacao.componentes()) {
            baixarComponente(producao, componente);
        }
        return verificacao;
    }

    private List<AvisoEstoqueNegativoResponse> avisosNaoConfirmados(List<ComponenteNecessidade> componentes,
                                                                       List<UUID> idsConfirmados) {
        List<AvisoEstoqueNegativoResponse> avisos = new ArrayList<>();
        for (ComponenteNecessidade componente : componentes) {
            boolean ficariaNegativo = componente.estoqueAtual().subtract(componente.necessaria())
                    .compareTo(BigDecimal.ZERO) < 0;
            if (!ficariaNegativo || !Boolean.TRUE.equals(componente.permitirEstoqueNegativo())) {
                continue;
            }
            UUID componenteId = componente.insumo() != null ? componente.insumo().getId() : componente.produtoBase().getId();
            if (idsConfirmados.contains(componenteId)) {
                continue;
            }
            AvisoEstoqueNegativoResponse aviso = new AvisoEstoqueNegativoResponse();
            aviso.setComponenteId(componenteId);
            aviso.setNome(componente.nome());
            aviso.setEstoqueAtual(componente.estoqueAtual());
            aviso.setQuantidadeNecessaria(componente.necessaria());
            aviso.setMensagem("A baixa de " + componente.necessaria().stripTrailingZeros().toPlainString()
                    + " de " + componente.nome() + " deixará o estoque negativo (atual: "
                    + componente.estoqueAtual().stripTrailingZeros().toPlainString() + "). Confirme para prosseguir.");
            avisos.add(aviso);
        }
        return avisos;
    }

    private ConfirmacaoEstoqueNegativoResponse montarConfirmacaoEstoqueNegativo(VerificacaoInsumos verificacao) {
        ConfirmacaoEstoqueNegativoResponse resposta = new ConfirmacaoEstoqueNegativoResponse();
        resposta.setAvisos(verificacao.avisosPendentes());
        return resposta;
    }

    /** Baixa efetiva de um componente — mesmo padrão de movimentação usado no fluxo legado (registrarProducao). */
    private void baixarComponente(Producao producao, ComponenteNecessidade componente) {
        BigDecimal consumida = componente.necessaria();

        if (componente.insumo() != null) {
            Insumo insumo = componente.insumo();
            insumo.setEstoqueAtual(insumo.getEstoqueAtual().subtract(consumida));
            insumoRepository.save(insumo);

            movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                    .insumo(insumo)
                    .tipo(TipoMovimentacaoInsumo.SAIDA)
                    .motivo(MotivoMovimentacaoInsumo.PRODUCAO)
                    .quantidade(consumida)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO)
                    .estornada(false)
                    .build());

            producaoInsumoConsumidoRepository.save(ProducaoInsumoConsumido.builder()
                    .producao(producao)
                    .insumo(insumo)
                    .quantidade(consumida)
                    .build());

        } else if (componente.produtoBase() != null) {
            Produto base = componente.produtoBase();
            base.setEstoqueAtual(base.getEstoqueAtual().subtract(consumida));
            produtoRepository.save(base);

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(base)
                    .tipo(TipoMovimentacaoProduto.SAIDA)
                    .motivo(MotivoMovimentacaoProduto.PRODUCAO)
                    .quantidade(consumida)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                    .estornada(false)
                    .build());

            producaoInsumoConsumidoRepository.save(ProducaoInsumoConsumido.builder()
                    .producao(producao)
                    .produtoBase(base)
                    .quantidade(consumida)
                    .build());
        }
    }

    /** Produtos já validados (existem, ativos, com ficha técnica + rendimento) e quantidades agregadas por RN-061. */
    private record ProdutosValidados(List<Produto> produtos, Map<UUID, BigDecimal> quantidades,
                                      Map<UUID, List<FichaTecnicaItem>> fichas) {
    }

    private void validarDatas(LocalDate dataInicio, LocalDate dataTerminoPrevista) {
        if (dataTerminoPrevista.isBefore(dataInicio)) {
            throw new BusinessException("Data de término prevista deve ser igual ou posterior à data de início");
        }
    }

    /** RN-061 — existência/ativo, duplicatas somadas; RN-077 — ficha técnica + rendimento obrigatórios. */
    private ProdutosValidados validarEResolverProdutos(List<ProducaoProdutoRequest> itens, UUID usuarioId) {
        Map<UUID, BigDecimal> quantidades = new LinkedHashMap<>();
        for (ProducaoProdutoRequest item : itens) {
            quantidades.merge(item.getProdutoId(), item.getQuantidade(), BigDecimal::add);
        }

        List<Produto> produtos = new ArrayList<>();
        Map<UUID, List<FichaTecnicaItem>> fichas = new LinkedHashMap<>();
        for (UUID produtoId : quantidades.keySet()) {
            Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new BusinessException("Produto " + produto.getNome() + " está inativo");
            }

            List<FichaTecnicaItem> ficha = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
            if (ficha.isEmpty() || produto.getRendimento() == null || produto.getRendimento().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Produto " + produto.getNome()
                        + " não tem ficha técnica completa ou rendimento válido — complete o cadastro antes de incluir em uma produção");
            }

            produtos.add(produto);
            fichas.put(produtoId, ficha);
        }

        return new ProdutosValidados(produtos, quantidades, fichas);
    }

    private List<ProducaoProduto> gravarProducaoProdutos(Producao producao, ProdutosValidados validados) {
        List<ProducaoProduto> gravados = new ArrayList<>();
        for (Produto produto : validados.produtos()) {
            gravados.add(producaoProdutoRepository.save(ProducaoProduto.builder()
                    .producao(producao)
                    .produto(produto)
                    .quantidade(validados.quantidades().get(produto.getId()))
                    .build()));
        }
        return gravados;
    }

    /**
     * RN-064 — alertas informativos de estoque, nunca bloqueiam a criação/edição. Consumo de cada
     * insumo é somado entre todos os produtos da produção antes de comparar com o estoque atual.
     */
    private List<AlertaInsumoResponse> calcularAlertas(ProdutosValidados validados) {
        Map<UUID, BigDecimal> necessidadePorComponente = new LinkedHashMap<>();
        Map<UUID, Insumo> insumosPorId = new LinkedHashMap<>();
        Map<UUID, Produto> produtosBasePorId = new LinkedHashMap<>();

        for (Produto produto : validados.produtos()) {
            BigDecimal quantidade = validados.quantidades().get(produto.getId());
            BigDecimal ratioLote = quantidade.divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

            for (FichaTecnicaItem item : validados.fichas().get(produto.getId())) {
                BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
                if (item.getInsumo() != null) {
                    necessidadePorComponente.merge(item.getInsumo().getId(), necessaria, BigDecimal::add);
                    insumosPorId.putIfAbsent(item.getInsumo().getId(), item.getInsumo());
                } else if (item.getProdutoBase() != null) {
                    necessidadePorComponente.merge(item.getProdutoBase().getId(), necessaria, BigDecimal::add);
                    produtosBasePorId.putIfAbsent(item.getProdutoBase().getId(), item.getProdutoBase());
                }
            }
        }

        List<AlertaInsumoResponse> alertas = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : necessidadePorComponente.entrySet()) {
            UUID componenteId = entry.getKey();
            BigDecimal necessaria = entry.getValue();

            String nome;
            BigDecimal estoqueAtual;
            Boolean permitirEstoqueNegativo;
            if (insumosPorId.containsKey(componenteId)) {
                Insumo insumo = insumosPorId.get(componenteId);
                nome = insumo.getNome();
                estoqueAtual = insumo.getEstoqueAtual();
                permitirEstoqueNegativo = insumo.getPermitirEstoqueNegativo();
            } else {
                Produto base = produtosBasePorId.get(componenteId);
                nome = base.getNome();
                estoqueAtual = base.getEstoqueAtual();
                permitirEstoqueNegativo = base.getPermitirEstoqueNegativo();
            }

            SituacaoAlertaInsumo situacao;
            if (estoqueAtual.compareTo(necessaria) >= 0) {
                situacao = SituacaoAlertaInsumo.SUFICIENTE;
            } else if (Boolean.FALSE.equals(permitirEstoqueNegativo)) {
                situacao = SituacaoAlertaInsumo.BLOQUEIO_FUTURO;
            } else {
                situacao = SituacaoAlertaInsumo.AVISO;
            }

            AlertaInsumoResponse alerta = new AlertaInsumoResponse();
            alerta.setNomeInsumo(nome);
            alerta.setEstoqueAtual(estoqueAtual);
            alerta.setQuantidadeNecessaria(necessaria);
            alerta.setSituacao(situacao);
            alertas.add(alerta);
        }
        return alertas;
    }

    /**
     * RN-064/#123 — mesmo cálculo de {@link #calcularAlertas(ProdutosValidados)}, mas a partir de
     * {@code ProducaoProduto} já persistido (GET /producoes e GET /producoes/{id}), sem passar pelas
     * validações de {@link #validarEResolverProdutos}. Produto com rendimento inválido é ignorado em
     * vez de lançar exceção — é um cálculo informativo de leitura, não pode quebrar o GET.
     */
    private List<AlertaInsumoResponse> calcularAlertasAoVivo(List<ProducaoProduto> produtosDaProducao) {
        Map<UUID, BigDecimal> necessidadePorComponente = new LinkedHashMap<>();
        Map<UUID, Insumo> insumosPorId = new LinkedHashMap<>();
        Map<UUID, Produto> produtosBasePorId = new LinkedHashMap<>();

        for (ProducaoProduto producaoProduto : produtosDaProducao) {
            Produto produto = producaoProduto.getProduto();
            if (produto.getRendimento() == null || produto.getRendimento().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal ratioLote = producaoProduto.getQuantidade().divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

            for (FichaTecnicaItem item : fichaTecnicaItemRepository.findByProdutoId(produto.getId())) {
                BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
                if (item.getInsumo() != null) {
                    necessidadePorComponente.merge(item.getInsumo().getId(), necessaria, BigDecimal::add);
                    insumosPorId.putIfAbsent(item.getInsumo().getId(), item.getInsumo());
                } else if (item.getProdutoBase() != null) {
                    necessidadePorComponente.merge(item.getProdutoBase().getId(), necessaria, BigDecimal::add);
                    produtosBasePorId.putIfAbsent(item.getProdutoBase().getId(), item.getProdutoBase());
                }
            }
        }

        List<AlertaInsumoResponse> alertas = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : necessidadePorComponente.entrySet()) {
            UUID componenteId = entry.getKey();
            BigDecimal necessaria = entry.getValue();

            String nome;
            BigDecimal estoqueAtual;
            Boolean permitirEstoqueNegativo;
            if (insumosPorId.containsKey(componenteId)) {
                Insumo insumo = insumosPorId.get(componenteId);
                nome = insumo.getNome();
                estoqueAtual = insumo.getEstoqueAtual();
                permitirEstoqueNegativo = insumo.getPermitirEstoqueNegativo();
            } else {
                Produto base = produtosBasePorId.get(componenteId);
                nome = base.getNome();
                estoqueAtual = base.getEstoqueAtual();
                permitirEstoqueNegativo = base.getPermitirEstoqueNegativo();
            }

            SituacaoAlertaInsumo situacao;
            if (estoqueAtual.compareTo(necessaria) >= 0) {
                situacao = SituacaoAlertaInsumo.SUFICIENTE;
            } else if (Boolean.FALSE.equals(permitirEstoqueNegativo)) {
                situacao = SituacaoAlertaInsumo.BLOQUEIO_FUTURO;
            } else {
                situacao = SituacaoAlertaInsumo.AVISO;
            }

            AlertaInsumoResponse alerta = new AlertaInsumoResponse();
            alerta.setNomeInsumo(nome);
            alerta.setEstoqueAtual(estoqueAtual);
            alerta.setQuantidadeNecessaria(necessaria);
            alerta.setSituacao(situacao);
            alertas.add(alerta);
        }
        return alertas;
    }

    private InsumoConsumidoResponse montarPreview(FichaTecnicaItem item, BigDecimal quantidadeFinal) {
        // RN-051 — mesma fórmula proporcional do fluxo antigo; dado legado anterior à RN-039 pode ter rendimento nulo.
        Produto produto = item.getProduto();
        exigirRendimentoValido(produto);
        BigDecimal ratioLote = quantidadeFinal.divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);
        BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
        InsumoConsumidoResponse response = new InsumoConsumidoResponse();
        response.setQuantidade(necessaria);

        BigDecimal estoqueAtual;
        if (item.getInsumo() != null) {
            Insumo insumo = item.getInsumo();
            response.setInsumoId(insumo.getId());
            response.setNomeInsumo(insumo.getNome());
            response.setMarca(insumo.getMarca());
            response.setUnidadeMedida(insumo.getUnidadeMedida());
            estoqueAtual = insumo.getEstoqueAtual();
        } else {
            Produto base = item.getProdutoBase();
            response.setInsumoId(base.getId());
            response.setNomeInsumo(base.getNome());
            estoqueAtual = base.getEstoqueAtual();
        }

        response.setEstoqueAntes(estoqueAtual);
        response.setEstoqueInsuficiente(estoqueAtual.compareTo(necessaria) < 0);
        return response;
    }

    /**
     * RN-039/RN-051 — rendimento é obrigatório desde o EP-04 para produto com ficha técnica preenchida,
     * mas produtos cadastrados antes dessa regra podem ter escapado da obrigatoriedade (dado legado).
     * Guarda de validação, não correção de dado — bloqueia com mensagem clara em vez de divisão nula/por zero.
     */
    private void exigirRendimentoValido(Produto produto) {
        if (produto.getRendimento() == null || produto.getRendimento().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Produto sem rendimento configurado — complete o cadastro do produto antes de lançar produção.");
        }
    }

    /** #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition. */
    private Integer proximoNumero(UUID usuarioId) {
        usuarioRepository.lockPorId(usuarioId);
        return producaoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId)
                .map(p -> p.getNumero() != null ? p.getNumero() + 1 : 1)
                .orElse(1);
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
