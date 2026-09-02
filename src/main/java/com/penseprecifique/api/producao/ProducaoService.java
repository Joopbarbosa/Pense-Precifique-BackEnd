package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
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
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ConsumoRealRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.FinalizarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.PerdaProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.request.producao.RetomarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.TravarProducaoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AgruparProducoesResponse;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.producao.DivisaoProducaoResponse;
import com.penseprecifique.api.shared.dto.response.producao.InsumoConsumidoResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ProducaoMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
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
import java.util.HashMap;
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
    private final OrcamentoProducaoRepository orcamentoProducaoRepository;

    // #158/RN-NOVA-6 (+ item avulso P-BE-NUMERO-SORT) — allowlist explícita dos 5 critérios de
    // ordenação aceitos em GET /producoes. "produto" e "quantidade" são agregados (MIN/SUM sobre
    // producao_produtos, já que uma Producao agrupa N produtos — RN-061) — a expressão JPQL
    // correspondente é resolvida aqui, nunca aceita do cliente. "numero" é campo direto da entidade
    // (mesmo padrão de dataInicio/estado, sem agregação) — já era o default sem sort explícito
    // (buscarIdsOrdenados agrupa por p.id, que é a PK; Postgres permite referenciar outras colunas
    // da mesma tabela por dependência funcional, sem exigir p.numero no GROUP BY). Campo fora da
    // allowlist é rejeitado com BusinessException (nunca ignorado em silêncio nem repassado cru pro
    // Sort, que exporia coluna interna via parâmetro).
    private static final Map<String, String> CAMPOS_ORDENACAO_PRODUCAO = Map.of(
            "dataInicio", "p.dataInicio",
            "estado", "p.estado",
            "produto", "MIN(pp.produto.nome)",
            "quantidade", "SUM(pp.quantidade)",
            "numero", "p.numero"
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
     * contra a allowlist (#354 — validação/tradução em si extraída para PageableOrdenacaoResolver,
     * reaproveitado também por Orçamento/Produto/Cliente/Insumo). Sem Sort informado → default
     * numero DESC (produção mais recente primeiro).
     */
    private Pageable resolverPageableOrdenado(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            Sort padrao = JpaSort.unsafe(Sort.Direction.DESC, "p.numero");
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), padrao);
        }
        return PageableOrdenacaoResolver.resolverExpressaoJpql(pageable, CAMPOS_ORDENACAO_PRODUCAO,
                "dataInicio, estado, produto, quantidade, numero");
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
        return producaoMapper.toResponse(producao, produtos, calcularAlertasAoVivo(produtos), historico,
                fichaTecnicaPorProduto(produtos));
    }

    /** #238 — tag global fracionável/estoque negativo/estoque atual por produto da produção. */
    private Map<UUID, List<FichaTecnicaItem>> fichaTecnicaPorProduto(List<ProducaoProduto> produtos) {
        Map<UUID, List<FichaTecnicaItem>> resultado = new HashMap<>();
        for (ProducaoProduto pp : produtos) {
            UUID produtoId = pp.getProduto().getId();
            resultado.computeIfAbsent(produtoId, fichaTecnicaItemRepository::findByProdutoId);
        }
        return resultado;
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

    /**
     * RN-PROD-VINC-03 (V0.8.2, #320) — alerta de insumo/rendimento do vínculo Orçamento↔Produção,
     * considerando a soma dos produtos já persistidos na produção com os produtos novos vindos do
     * orçamento (não cada um isoladamente). Reaproveita {@link #validarEResolverProdutos}, que já
     * mescla quantidades duplicadas do mesmo produto por {@code Map.merge()}, então basta concatenar
     * as duas listas antes de validar — nenhuma lógica de soma nova aqui, e {@link #calcularAlertas}
     * (motor existente) não é duplicado. Mesma checagem de estado (RN-PROD-VINC-02) de
     * {@link #adicionarProdutosDeOrcamento} — falha rápido em vez de mostrar um alerta que a
     * confirmação real não vai conseguir persistir.
     */
    @Transactional(readOnly = true)
    public List<AlertaInsumoResponse> calcularAlertasComAdicao(UUID producaoId, List<ProducaoProdutoRequest> produtosNovos) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(producaoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Essa produção já começou e não pode receber novos itens");
        }

        List<ProducaoProdutoRequest> combinados = new ArrayList<>();
        for (ProducaoProduto existente : producaoProdutoRepository.findByProducaoId(producao.getId())) {
            ProducaoProdutoRequest request = new ProducaoProdutoRequest();
            request.setProdutoId(existente.getProduto().getId());
            request.setQuantidade(existente.getQuantidade());
            combinados.add(request);
        }
        combinados.addAll(produtosNovos);

        ProdutosValidados validados = validarEResolverProdutos(combinados, usuarioId);
        return calcularAlertas(validados);
    }

    /** RN-061/062/064/077 — cria produção com N produtos, sem movimentação de estoque. Nasce AGUARDANDO_INICIO. */
    public ProducaoDetalheResponse criarProducao(CriarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Producao producao = criarProducaoBase(usuarioId, request.getDataInicio(), request.getDataTerminoPrevista(),
                request.getObservacoes());

        ProdutosValidados validados = validarEResolverProdutos(request.getProdutos(), usuarioId);
        List<ProducaoProduto> produtosGravados = gravarProducaoProdutos(producao, validados);

        List<AlertaInsumoResponse> alertas = calcularAlertas(validados);
        return montarDetalhe(producao, alertas);
    }

    /**
     * P-B020 (V0.8.2, #320) — núcleo de criação de {@link Producao} sem produtos, extraído de
     * {@link #criarProducao} para ser reaproveitado por {@code OrcamentoService.criarProducaoVinculada()}
     * (RN-ORC-VINC-05), que grava os produtos à parte via {@link #adicionarProdutosDeOrcamento} — a
     * produção nasce sem nenhum {@code ProducaoProduto}, então o merge-por-produto daquele método
     * nunca soma em cima de nada (todo produto entra pela primeira vez), sem risco de duplicar
     * quantidade. Note que a ordem de validação (datas antes de produtos) é preservada em
     * {@link #criarProducao} mesmo depois da extração — quem chama decide a ordem, este método só
     * valida as datas que recebe.
     */
    public Producao criarProducaoBase(UUID usuarioId, LocalDate dataInicio, LocalDate dataTerminoPrevista,
                                       String observacoes) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));

        LocalDate inicio = dataInicio != null ? dataInicio : LocalDate.now();
        validarDatas(inicio, dataTerminoPrevista);

        Producao producao = Producao.builder()
                .usuario(usuario)
                .numero(proximoNumero(usuarioId))
                .estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(inicio)
                .dataTerminoPrevista(dataTerminoPrevista)
                .observacoes(observacoes)
                .build();
        producao = producaoRepository.save(producao);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(null)
                .statusNovo(EstadoProducao.AGUARDANDO_INICIO)
                .origem(OrigemHistoricoStatus.USUARIO)
                .build());

        return producao;
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
                    .custoUnitario(insumo.getCustoUnitario())
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
    public Object retomar(UUID id, RetomarProducaoRequest request) {
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

    /** Cria uma produção filha (DIVISAO) copiando dataInicio/dataTerminoPrevista/observacoes da origem
     *  e gravando os ProducaoProduto informados. Nasce sem histórico de status — quem chama transiciona
     *  via registrarNascimento()/transicionar(). RN-PROD-VINC-04 (P-B018, #320) — também propaga, para
     *  cada produto que a filha recebe, o histórico de origem (ITEM_ADICIONADO) e o vínculo
     *  (orcamento_producoes) dos orçamentos que efetivamente contribuíram, via
     *  {@link #propagarOrigemParaFilha}. */
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

        propagarOrigemParaFilha(origem, filha, produtosOrigem);
        return filha;
    }

    /**
     * RN-PROD-VINC-04 (P-B018, #320) — como {@code dividir()} nunca fraciona a quantidade de um
     * produto entre as duas filhas (cada {@link ProducaoProduto}, com sua quantidade inteira, vai
     * para uma única filha — {@code UNIQUE(producao_id, produto_id)}, P-B015), propagar histórico não
     * exige matemática de proporção: a filha que recebe o produto recebe também <b>todo</b> o
     * histórico de origem daquele produto, de todos os orçamentos que já contribuíram para ele.
     *
     * <p>Para cada produto, calcula o <b>saldo líquido por orçamento</b>
     * ({@code ITEM_ADICIONADO} − {@code ITEM_REMOVIDO}, agrupado por {@code referencia_orcamento_id})
     * em vez de copiar cegamente todo {@code ITEM_ADICIONADO} já gravado — um orçamento pode ter sido
     * parcial ou totalmente desvinculado (P-B017) antes desta divisão, e copiar a quantidade bruta
     * original ressuscitaria uma reversão já efetivada. Só orçamentos com saldo positivo são
     * propagados, com a quantidade líquida (não a bruta).
     *
     * <p>Cada linha propagada é uma {@code ITEM_ADICIONADO} nova na filha (histórico append-only,
     * nunca reaproveita/move a linha original da produção-mãe) com {@code origem=SISTEMA} (a artesã
     * não pediu para adicionar nada — é efeito automático da divisão). Cada orçamento com saldo
     * positivo em pelo menos um produto da filha ganha um vínculo novo em {@code orcamento_producoes}
     * para a filha — o vínculo da produção-mãe original (que vira {@code NAO_REALIZADA}) permanece
     * intocado, nunca removido (mesmo padrão append-only: a divisão não é um desvincular).
     */
    private void propagarOrigemParaFilha(Producao origem, Producao filha, List<ProducaoProduto> produtosOrigem) {
        Map<UUID, Orcamento> orcamentosParaVincular = new LinkedHashMap<>();

        for (ProducaoProduto producaoProduto : produtosOrigem) {
            UUID produtoId = producaoProduto.getProduto().getId();
            Map<UUID, BigDecimal> saldoPorOrcamento = new LinkedHashMap<>();
            Map<UUID, Orcamento> orcamentosDoProduto = new LinkedHashMap<>();

            for (HistoricoStatusProducao linha : historicoStatusProducaoRepository
                    .findByProducaoIdAndProdutoIdAndTipoEventoIn(origem.getId(), produtoId,
                            List.of(TipoEventoHistoricoProducao.ITEM_ADICIONADO, TipoEventoHistoricoProducao.ITEM_REMOVIDO))) {
                UUID orcamentoId = linha.getReferenciaOrcamento().getId();
                BigDecimal delta = linha.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO
                        ? linha.getQuantidade() : linha.getQuantidade().negate();
                saldoPorOrcamento.merge(orcamentoId, delta, BigDecimal::add);
                orcamentosDoProduto.putIfAbsent(orcamentoId, linha.getReferenciaOrcamento());
            }

            for (Map.Entry<UUID, BigDecimal> entry : saldoPorOrcamento.entrySet()) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Orcamento orcamento = orcamentosDoProduto.get(entry.getKey());
                historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                        .producao(filha)
                        .tipoEvento(TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                        .produto(producaoProduto.getProduto())
                        .quantidade(entry.getValue())
                        .referenciaOrcamento(orcamento)
                        .origem(OrigemHistoricoStatus.SISTEMA)
                        .build());
                orcamentosParaVincular.putIfAbsent(orcamento.getId(), orcamento);
            }
        }

        for (Orcamento orcamento : orcamentosParaVincular.values()) {
            orcamentoProducaoRepository.save(OrcamentoProducao.builder()
                    .orcamento(orcamento)
                    .producao(filha)
                    .build());
        }
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
        List<OrcamentoProducao> orcamentosVinculados = orcamentoProducaoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos, produtos, alertasInsumos, historico, producoesFilhas,
                fichaTecnicaPorProduto(produtos), orcamentosVinculados);
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
                    .custoUnitario(insumo.getCustoUnitario())
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

            boolean algumInsumoNaoFracionavel = ficha.stream()
                    .anyMatch(item -> item.getInsumo() != null && Boolean.FALSE.equals(item.getInsumo().getFracionavel()));
            if (algumInsumoNaoFracionavel) {
                validarMultiploDoRendimento(produto, ficha, quantidades.get(produtoId));
            }

            produtos.add(produto);
            fichas.put(produtoId, ficha);
        }

        return new ProdutosValidados(produtos, quantidades, fichas);
    }

    /**
     * PDC-027 — substitui PDC-005 (Reversão #214). Produto com algum insumo não-fracionável na ficha
     * já não trava mais em exatamente 1x o rendimento: aceita qualquer múltiplo inteiro, limitado ao
     * estoque disponível dos insumos não-fracionáveis que não permitem estoque negativo.
     *
     * Correção (regressão achada no Frontend de #214, decisão de negócio confirmada 2026-08-08): o
     * teto por estoque só se aplica quando há pelo menos 1x de estoque disponível (maxMultiplos >= 1).
     * Estoque insuficiente para nem 1x o rendimento (maxMultiplos = 0) NÃO bloqueia a criação aqui —
     * a produção segue o fluxo pré-existente de trava por estoque insuficiente (TRAVADA ao tentar
     * Iniciar, com Dividir/Travar tudo/Retomar), que já existia antes de #214 e não muda.
     */
    private void validarMultiploDoRendimento(Produto produto, List<FichaTecnicaItem> ficha, BigDecimal quantidadeInformada) {
        BigDecimal rendimento = produto.getRendimento();
        if (quantidadeInformada.remainder(rendimento).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Produto " + produto.getNome()
                    + " exige quantidade em múltiplos de " + rendimento + " unidades");
        }

        BigDecimal multiploMaximoPermitido = null;
        String insumoLimitante = null;
        for (FichaTecnicaItem item : ficha) {
            Insumo insumo = item.getInsumo();
            boolean naoFracionavel = insumo != null && Boolean.FALSE.equals(insumo.getFracionavel());
            if (!naoFracionavel || !Boolean.FALSE.equals(insumo.getPermitirEstoqueNegativo())) {
                continue;
            }

            BigDecimal maxMultiplos = insumo.getEstoqueAtual().divideToIntegralValue(item.getQuantidade());
            if (maxMultiplos.compareTo(BigDecimal.ZERO) == 0) {
                // Sem estoque para nem 1x o rendimento — não limita a criação, fica para a trava pós-criação.
                continue;
            }
            if (multiploMaximoPermitido == null || maxMultiplos.compareTo(multiploMaximoPermitido) < 0) {
                multiploMaximoPermitido = maxMultiplos;
                insumoLimitante = insumo.getNome();
            }
        }

        if (multiploMaximoPermitido != null) {
            BigDecimal quantidadeMaxima = multiploMaximoPermitido.multiply(rendimento);
            if (quantidadeInformada.compareTo(quantidadeMaxima) > 0) {
                throw new BusinessException("Produto " + produto.getNome()
                        + ": quantidade máxima permitida é " + quantidadeMaxima
                        + " unidades, limitado pelo estoque de " + insumoLimitante);
            }
        }
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
     * RN-PROD-VINC-01/02 (V0.8.2, #320) — chamado por {@code OrcamentoService.vincularProducao()}
     * quando um orçamento vincula produtos a uma produção existente. A validação de estado
     * (RN-PROD-VINC-02) vive aqui, não em OrcamentoService — é regra do ciclo de vida de Produção,
     * mesmo padrão já usado em {@link #editarProducao}. Produto já presente na produção tem a
     * quantidade somada (merge, nunca duplica {@code ProducaoProduto}) — mesmo espírito de
     * agrupamento de PDC-001, agora também contra o que já está persistido, não só duplicatas dentro
     * do mesmo request. Grava 1 linha {@code ITEM_ADICIONADO} por produto (não agregada): a
     * rastreabilidade de origem (RN-PROD-HIST-01) precisa de produto_id/quantidade por linha para o
     * desvincular futuro conseguir reverter exatamente o que aquele orçamento adicionou.
     */
    public List<ProducaoProduto> adicionarProdutosDeOrcamento(UUID producaoId, List<ProducaoProdutoRequest> produtos,
                                                                UUID usuarioId, Orcamento referenciaOrcamento) {
        Producao producao = producaoRepository.findByIdAndUsuarioId(producaoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Essa produção já começou e não pode receber novos itens");
        }

        ProdutosValidados validados = validarEResolverProdutos(produtos, usuarioId);

        List<ProducaoProduto> resultado = new ArrayList<>();
        for (Produto produto : validados.produtos()) {
            BigDecimal quantidade = validados.quantidades().get(produto.getId());

            ProducaoProduto producaoProduto = producaoProdutoRepository
                    .findByProducaoIdAndProdutoId(producao.getId(), produto.getId())
                    .map(existente -> {
                        existente.setQuantidade(existente.getQuantidade().add(quantidade));
                        return producaoProdutoRepository.save(existente);
                    })
                    .orElseGet(() -> producaoProdutoRepository.save(ProducaoProduto.builder()
                            .producao(producao)
                            .produto(produto)
                            .quantidade(quantidade)
                            .build()));
            resultado.add(producaoProduto);

            historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                    .producao(producao)
                    .tipoEvento(TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                    .produto(produto)
                    .quantidade(quantidade)
                    .referenciaOrcamento(referenciaOrcamento)
                    .origem(OrigemHistoricoStatus.USUARIO)
                    .build());
        }

        return resultado;
    }

    /**
     * P-B017 (#320) — soma, por produto, as linhas {@code ITEM_ADICIONADO} já registradas para um
     * par orçamento+produção específico. Usado por {@code OrcamentoService.vincularProducao()} para
     * calcular o que falta sincronizar (RN-ORC-VINC-03, achado de re-sincronização de P-B015): vincular
     * a mesma produção mais de uma vez só reenvia o delta (produto novo ou quantidade aumentada), nunca
     * re-soma o que já está registrado. Soma por produto porque uma re-sincronização anterior pode ter
     * gravado mais de 1 linha para o mesmo produto (1 por chamada de {@link #adicionarProdutosDeOrcamento}).
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> produtosJaAdicionadosPeloOrcamento(UUID producaoId, UUID referenciaOrcamentoId) {
        Map<UUID, BigDecimal> jaSincronizado = new HashMap<>();
        for (HistoricoStatusProducao linha : historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                        producaoId, referenciaOrcamentoId, TipoEventoHistoricoProducao.ITEM_ADICIONADO)) {
            jaSincronizado.merge(linha.getProduto().getId(), linha.getQuantidade(), BigDecimal::add);
        }
        return jaSincronizado;
    }

    /**
     * RN-ORC-VINC-03 (V0.8.2, #320) — chamado por {@code OrcamentoService.desvincularProducao()} para
     * reverter o que aquele orçamento adicionou à produção. Mesma restrição de estado de
     * {@link #adicionarProdutosDeOrcamento} (RN-PROD-VINC-02, decisão de simetria — desvincular só é
     * aceito com a produção ainda em {@code AGUARDANDO_INICIO}, mesmo motivo: depois que a produção
     * começa, insumo pode já ter sido baixado e o trabalho físico já está em andamento).
     *
     * <p>Itera <b>linha por linha do histórico</b> {@code ITEM_ADICIONADO} daquele orçamento naquela
     * produção — nunca pelo total agregado do produto — porque o mesmo {@link ProducaoProduto} pode
     * ter recebido contribuição de mais de uma origem (2+ orçamentos vinculados à mesma produção,
     * somando no mesmo produto). Decrementar pelo total do produto zeraria também a contribuição de
     * outro orçamento; decrementar linha a linha (já filtrada por {@code referencia_orcamento_id})
     * nunca toca no que outro orçamento adicionou.
     *
     * <p>Piso em zero (nunca negativo) — mesmo padrão de "desconta X de Y" já usado no projeto em
     * {@code OrcamentoService.calcularValorFinalMulta()} — cobre o caso raro de a produção ter sido
     * editada manualmente depois do vínculo, perdendo rastreabilidade exata. Quando a quantidade
     * chega a zero, a linha de {@link ProducaoProduto} é removida (não fica um produto "fantasma" com
     * quantidade zero na produção) — a próxima sincronização (se houver) recria a linha do zero.
     *
     * <p>Grava 1 linha {@code ITEM_REMOVIDO} por linha revertida (mesma granularidade de
     * {@code ITEM_ADICIONADO}), nunca apaga/edita a linha original — histórico é append-only.
     */
    public void removerProdutosDeOrcamento(UUID producaoId, UUID referenciaOrcamentoId, UUID usuarioId) {
        Producao producao = producaoRepository.findByIdAndUsuarioId(producaoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Essa produção já começou e não pode ter itens removidos");
        }

        List<HistoricoStatusProducao> adicionados = historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                        producaoId, referenciaOrcamentoId, TipoEventoHistoricoProducao.ITEM_ADICIONADO);

        for (HistoricoStatusProducao linha : adicionados) {
            Produto produto = linha.getProduto();
            producaoProdutoRepository.findByProducaoIdAndProdutoId(producaoId, produto.getId())
                    .ifPresent(producaoProduto -> {
                        BigDecimal restante = producaoProduto.getQuantidade().subtract(linha.getQuantidade())
                                .max(BigDecimal.ZERO);
                        if (restante.compareTo(BigDecimal.ZERO) == 0) {
                            producaoProdutoRepository.delete(producaoProduto);
                        } else {
                            producaoProduto.setQuantidade(restante);
                            producaoProdutoRepository.save(producaoProduto);
                        }
                    });

            historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                    .producao(producao)
                    .tipoEvento(TipoEventoHistoricoProducao.ITEM_REMOVIDO)
                    .produto(produto)
                    .quantidade(linha.getQuantidade())
                    .referenciaOrcamento(linha.getReferenciaOrcamento())
                    .origem(OrigemHistoricoStatus.USUARIO)
                    .build());
        }
    }

    /**
     * RN-NOVA-17 (V0.8.3, #375+308, P-S001c) — "Não, remover": remove a contribuição de UM produto
     * específico feita por UM orçamento, numa produção já {@code EM_ANDAMENTO}/{@code TRAVADA}
     * (insumo já baixado, trabalho físico em andamento — {@link #removerProdutosDeOrcamento} já não
     * aceita mais neste ponto, RN-PROD-VINC-02). Mecanismo <b>novo e deliberadamente independente</b>
     * de {@link #adicionarProdutosDeOrcamento}/{@link #editarProducao} — não reaproveita nem
     * contorna a trava de {@code AGUARDANDO_INICIO} desses dois métodos, é um terceiro caminho com
     * validação de estado própria (exige exatamente o oposto: {@code EM_ANDAMENTO}/{@code TRAVADA}).
     *
     * <p><b>AVISO, nunca BLOQUEIO</b> (decisão do usuário, P-S001c) — remove a linha
     * {@code ProducaoProduto} (piso zero, mesmo padrão de {@link #removerProdutosDeOrcamento}) e
     * registra {@code ITEM_REMOVIDO}, mas <b>nunca dispara movimentação de estoque</b>: o insumo já
     * baixado permanece baixado, mesmo padrão-default já usado por {@code aplicarConsumoReal()}/
     * RN-072 ("ausência de declaração de consumo real = consumo total assumido, sem estorno").
     */
    public void removerProdutoDeProducaoAtiva(UUID producaoId, UUID produtoId, UUID referenciaOrcamentoId,
                                               UUID usuarioId) {
        Producao producao = producaoRepository.findByIdAndUsuarioId(producaoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.EM_ANDAMENTO && producao.getEstado() != EstadoProducao.TRAVADA) {
            throw new BusinessException("Esta remoção só se aplica a produções em andamento ou travadas — "
                    + "produções aguardando início usam o desvincular normal, que reverte o produto");
        }

        List<HistoricoStatusProducao> adicionados = historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndProdutoIdAndTipoEvento(
                        producaoId, referenciaOrcamentoId, produtoId, TipoEventoHistoricoProducao.ITEM_ADICIONADO);
        if (adicionados.isEmpty()) {
            throw new ResourceNotFoundException("Este orçamento não contribuiu com este produto nesta produção");
        }

        BigDecimal quantidadeContribuida = adicionados.stream()
                .map(HistoricoStatusProducao::getQuantidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ProducaoProduto producaoProduto = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producaoId, produtoId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado nesta produção"));

        BigDecimal restante = producaoProduto.getQuantidade().subtract(quantidadeContribuida).max(BigDecimal.ZERO);
        if (restante.compareTo(BigDecimal.ZERO) == 0) {
            producaoProdutoRepository.delete(producaoProduto);
        } else {
            producaoProduto.setQuantidade(restante);
            producaoProdutoRepository.save(producaoProduto);
        }

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .tipoEvento(TipoEventoHistoricoProducao.ITEM_REMOVIDO)
                .produto(adicionados.get(0).getProduto())
                .quantidade(quantidadeContribuida)
                .referenciaOrcamento(adicionados.get(0).getReferenciaOrcamento())
                .origem(OrigemHistoricoStatus.USUARIO)
                .build());
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
