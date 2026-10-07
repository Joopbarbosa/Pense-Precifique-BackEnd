package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.compra.CompraItemRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.compra.FornecedorInsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteGraficosResponse;
import com.penseprecifique.api.shared.dto.response.cliente.CompraFornecedorResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteIndicadoresResponse;
import com.penseprecifique.api.shared.dto.response.cliente.IndicadoresClienteResponse;
import com.penseprecifique.api.shared.dto.response.cliente.IndicadoresFornecedorResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ItemCompradoResponse;
import com.penseprecifique.api.shared.dto.response.cliente.PedidoClienteResponse;
import com.penseprecifique.api.shared.dto.response.cliente.QuantidadeValorResponse;
import com.penseprecifique.api.shared.dto.response.cliente.RegistroCadastroResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.shared.texto.TextoNormalizado;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #560/#451 (V0.15.0, DT-NOVA-9) — histórico, indicadores e gráficos da página de detalhe do
 * cadastro. Agregação síncrona ao vivo, sem cache nem tabela de resumo: o volume por cadastro é
 * pequeno (dezenas a centenas de pedidos), então os pedidos são carregados e agregados em memória.
 *
 * <p>Compra de cliente (Decisão 13): orçamento ENTREGUE (data = {@code dataEntrega}) + venda do Caixa
 * CONCLUIDA (data = {@code dataVenda}). O histórico lista todos os pedidos, com o status visível;
 * o critério de compra vale só para indicadores e gráficos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClienteHistoricoService {

    static final String TIPO_ORCAMENTO = "ORCAMENTO";
    static final String TIPO_VENDA_CAIXA = "VENDA_CAIXA";
    private static final int MAX_ITENS_GRAFICO = 10;

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final VendaCaixaRepository vendaCaixaRepository;
    private final VendaCaixaItemRepository vendaCaixaItemRepository;
    private final CompraRepository compraRepository;
    private final CompraItemRepository compraItemRepository;
    private final FornecedorInsumoRepository fornecedorInsumoRepository;

    /** Linha interna: pedido + itens (só carregados para os que contam como compra). */
    private record Pedido(PedidoClienteResponse resumo, List<LinhaItem> itens) {}

    private record LinhaItem(UUID id, String tipo, String nome, BigDecimal quantidade, BigDecimal valor) {}

    public ClienteIndicadoresResponse indicadores(UUID clienteId) {
        UUID usuarioId = validarCadastro(clienteId);
        List<Orcamento> orcamentos = orcamentoRepository.findByClienteIdAndUsuarioIdAndDeletedAtIsNull(clienteId, usuarioId);
        List<Pedido> compras = comprasComItens(orcamentos,
                vendaCaixaRepository.findByClienteIdAndUsuarioId(clienteId, usuarioId));

        BigDecimal totalGasto = compras.stream().map(p -> p.resumo().valor())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long numeroPedidos = compras.size();
        BigDecimal ticketMedio = numeroPedidos == 0 ? null
                : totalGasto.divide(BigDecimal.valueOf(numeroPedidos), 2, RoundingMode.HALF_UP);
        PedidoClienteResponse ultima = compras.stream().map(Pedido::resumo)
                .max(Comparator.comparing(PedidoClienteResponse::dataCompra)).orElse(null);
        LocalDateTime desde = compras.stream().map(p -> p.resumo().dataCompra())
                .min(Comparator.naturalOrder()).orElse(null);
        List<ItemCompradoResponse> ranking = rankingItens(compras);

        IndicadoresClienteResponse cliente = new IndicadoresClienteResponse(
                ultima, totalGasto, ticketMedio, numeroPedidos,
                ranking.isEmpty() ? null : ranking.get(0),
                desde,
                somar(orcamentos.stream().filter(o -> o.getStatus() != StatusOrcamento.ENTREGUE
                        && o.getStatus() != StatusOrcamento.CANCELADO).toList()),
                somar(orcamentos.stream().filter(o -> o.getStatus() == StatusOrcamento.CANCELADO).toList()));

        IndicadoresFornecedorResponse fornecedor = indicadoresFornecedor(clienteId, usuarioId);
        return new ClienteIndicadoresResponse(cliente, fornecedor);
    }

    /** Aba Histórico, seção Cliente: todos os orçamentos (qualquer status) e vendas do Caixa. */
    public Page<PedidoClienteResponse> historicoPedidos(UUID clienteId, Pageable pageable) {
        UUID usuarioId = validarCadastro(clienteId);
        List<PedidoClienteResponse> todos = new ArrayList<>();
        orcamentoRepository.findByClienteIdAndUsuarioIdAndDeletedAtIsNull(clienteId, usuarioId)
                .forEach(o -> todos.add(resumo(o)));
        vendaCaixaRepository.findByClienteIdAndUsuarioId(clienteId, usuarioId)
                .forEach(v -> todos.add(resumo(v)));
        todos.sort(Comparator.comparing(PedidoClienteResponse::data).reversed()
                .thenComparing(PedidoClienteResponse::identificador));
        return paginar(todos, pageable);
    }

    // ------------------------------------------------------------------ modal de listagem (registros)

    /** Linha interna da listagem: a resposta + o que serve só para filtrar/ordenar. */
    private record Registro(RegistroCadastroResponse resposta, int numero, Collection<UUID> itemIds, List<String> nomesItens,
                            boolean comDesconto) {
        Registro(RegistroCadastroResponse resposta, int numero, Collection<UUID> itemIds, List<String> nomesItens) {
            this(resposta, numero, itemIds, nomesItens, false);
        }
    }

    private static final Map<String, Comparator<Registro>> ORDENACAO_REGISTROS = Map.of(
            "data", Comparator.comparing((Registro r) -> r.resposta().data()),
            "identificador", Comparator.comparing((Registro r) -> r.resposta().tipo()).thenComparingInt(Registro::numero),
            "valor", Comparator.comparing((Registro r) -> r.resposta().valor()),
            "status", Comparator.comparing((Registro r) -> r.resposta().status()));

    /** Desempate estável: mais recente primeiro, depois o maior número. */
    private static final Comparator<Registro> DESEMPATE = ORDENACAO_REGISTROS.get("data").reversed()
            .thenComparing(ORDENACAO_REGISTROS.get("identificador").reversed());

    /**
     * Ajuste do teste manual (V0.15.0, #560/#451) — modal de listagem do detalhe do cadastro, aberta pela
     * lupa dos indicadores e pelo clique nos gráficos. Mesma agregação em memória do histórico
     * (DT-NOVA-9); busca, filtros e ordenação no servidor, como toda listagem do sistema.
     *
     * <p>{@code papel} CLIENTE lista orçamentos (qualquer status) e vendas do Caixa; FORNECEDOR lista as
     * compras em que o cadastro aparece. {@code somenteCompras}: só o que conta nos indicadores
     * (orçamento ENTREGUE, venda CONCLUIDA, compra CONFIRMADA). {@code naoPagas}: compras confirmadas
     * não pagas. {@code itemId}: produto/item de catálogo (cliente) ou insumo (fornecedor).
     * {@code busca}: número ou nome de item, sem diferenciar acento nem maiúscula. {@code status}: um ou
     * mais (ex.: "em aberto" = RASCUNHO a PAGO). RN-NOVA-24 / DT-NOVA-20.
     */
    public Page<RegistroCadastroResponse> registros(UUID cadastroId, PapelCadastro papel, String busca, List<String> status,
                                                    boolean somenteCompras, boolean naoPagas, LocalDate de,
                                                    LocalDate ate, UUID itemId, Pageable pageable) {
        return registrosComFiltros(cadastroId, papel, busca, status, somenteCompras, naoPagas, de, ate,
                itemId == null ? List.of() : List.of(itemId), List.of(), null, false, pageable);
    }

    /**
     * #585/RN-NOVA-35 (adendo 2) — campo de filtros da modal: vários itens somam como OU; {@code tipos}
     * (ORCAMENTO, VENDA_CAIXA, COMPRA); {@code pago} nulo = tanto faz (vale para compra do fornecedor);
     * {@code comDesconto}: compra com desconto de linha ou de nota nas linhas deste fornecedor.
     */
    public Page<RegistroCadastroResponse> registrosComFiltros(UUID cadastroId, PapelCadastro papel, String busca, List<String> status,
                                                    boolean somenteCompras, boolean naoPagas, LocalDate de,
                                                    LocalDate ate, Collection<UUID> itemIds, Collection<String> tipos,
                                                    Boolean pago, boolean comDesconto, Pageable pageable) {
        UUID usuarioId = validarCadastro(cadastroId);
        if (papel == null) {
            throw new BusinessException("Informe o papel: CLIENTE ou FORNECEDOR.");
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw BusinessException.explicado("Período inválido", "A data inicial não pode ser depois da data final.",
                    "O período vai da data inicial até a data final.",
                    "Troque as datas de lugar (ex.: de 01/09/2026 até 30/09/2026).");
        }
        Comparator<Registro> ordem = ordenacaoRegistros(pageable.getSort());
        String termo = normalizar(busca);

        List<Registro> todos = papel == PapelCadastro.CLIENTE
                ? registrosCliente(cadastroId, usuarioId)
                : registrosFornecedor(cadastroId, usuarioId);
        List<RegistroCadastroResponse> filtrados = todos.stream()
                .filter(r -> status == null || status.isEmpty() || status.contains(r.resposta().status()))
                .filter(r -> !somenteCompras || r.resposta().contaComoCompra())
                .filter(r -> !naoPagas || (r.resposta().contaComoCompra() && Boolean.FALSE.equals(r.resposta().pago())))
                .filter(r -> de == null || !r.resposta().data().isBefore(de))
                .filter(r -> ate == null || !r.resposta().data().isAfter(ate))
                .filter(r -> itemIds == null || itemIds.isEmpty() || r.itemIds().stream().anyMatch(itemIds::contains))
                .filter(r -> tipos == null || tipos.isEmpty() || tipos.contains(r.resposta().tipo()))
                .filter(r -> pago == null || pago.equals(r.resposta().pago()))
                .filter(r -> !comDesconto || r.comDesconto())
                .filter(r -> termo.isEmpty() || normalizar(r.resposta().identificador()).contains(termo)
                        || r.nomesItens().stream().anyMatch(n -> normalizar(n).contains(termo)))
                .sorted(ordem)
                .map(Registro::resposta)
                .toList();
        return paginar(filtrados, pageable);
    }

    private static Comparator<Registro> ordenacaoRegistros(Sort sort) {
        Comparator<Registro> ordem = null;
        for (Sort.Order o : sort) {
            Comparator<Registro> campo = ORDENACAO_REGISTROS.get(o.getProperty());
            if (campo == null) {
                throw new BusinessException("Campo de ordenação inválido: '" + o.getProperty()
                        + "'. Permitidos: data, identificador, valor, status.");
            }
            campo = o.isAscending() ? campo : campo.reversed();
            ordem = ordem == null ? campo : ordem.thenComparing(campo);
        }
        return ordem == null ? DESEMPATE : ordem.thenComparing(DESEMPATE);
    }

    private List<Registro> registrosCliente(UUID clienteId, UUID usuarioId) {
        List<Orcamento> orcamentos = orcamentoRepository.findByClienteIdAndUsuarioIdAndDeletedAtIsNull(clienteId, usuarioId);
        List<VendaCaixa> vendas = vendaCaixaRepository.findByClienteIdAndUsuarioId(clienteId, usuarioId);
        Map<UUID, List<OrcamentoItem>> itensOrc = orcamentos.isEmpty() ? Map.of()
                : orcamentoItemRepository.findByOrcamentoIdIn(orcamentos.stream().map(Orcamento::getId).toList())
                        .stream().collect(Collectors.groupingBy(i -> i.getOrcamento().getId()));
        Map<UUID, List<VendaCaixaItem>> itensVenda = vendas.isEmpty() ? Map.of()
                : vendaCaixaItemRepository.findByVendaCaixaIdIn(vendas.stream().map(VendaCaixa::getId).toList())
                        .stream().collect(Collectors.groupingBy(i -> i.getVendaCaixa().getId()));

        List<Registro> registros = new ArrayList<>();
        orcamentos.forEach(o -> registros.add(registroPedido(resumo(o), o.getNumero(),
                itensOrc.getOrDefault(o.getId(), List.of()).stream()
                        .map(ClienteHistoricoService::linha).filter(Objects::nonNull).toList())));
        vendas.forEach(v -> registros.add(registroPedido(resumo(v), v.getNumero(),
                itensVenda.getOrDefault(v.getId(), List.of()).stream()
                        .map(ClienteHistoricoService::linha).filter(Objects::nonNull).toList())));
        return registros;
    }

    private static Registro registroPedido(PedidoClienteResponse p, int numero, List<LinhaItem> linhas) {
        LocalDateTime data = p.contaComoCompra() ? p.dataCompra() : p.data();
        String resumo = linhas.stream().map(l -> l.nome() + " ×" + quantidade(l.quantidade()))
                .collect(Collectors.joining(", "));
        return new Registro(new RegistroCadastroResponse(p.id(), p.tipo(), p.identificador(), data.toLocalDate(),
                p.status(), p.valor(), linhas.size(), resumo, p.contaComoCompra(), null),
                numero, linhas.stream().map(LinhaItem::id).toList(), linhas.stream().map(LinhaItem::nome).toList());
    }

    private List<Registro> registrosFornecedor(UUID fornecedorId, UUID usuarioId) {
        return linhasDoFornecedor(fornecedorId, usuarioId).entrySet().stream().map(e -> {
            Compra c = e.getKey();
            List<CompraItem> linhas = e.getValue();
            String resumo = CompraMapper.resumoItens(linhas);
            return new Registro(new RegistroCadastroResponse(c.getId(), "COMPRA",
                    IdentificadorFormatter.formatar("COM", c.getNumero()), c.getDataCompra(), c.getStatus().name(),
                    CompraMapper.total(linhas), linhas.size(), resumo, c.getStatus() == StatusCompra.CONFIRMADA,
                    Boolean.TRUE.equals(c.getPago())),
                    c.getNumero(), linhas.stream().map(i -> i.getInsumo().getId()).toList(),
                    linhas.stream().map(i -> i.getInsumo().getNome()).toList(),
                    linhas.stream().anyMatch(i -> positivo(i.getDescontoLinha()) || positivo(i.getDescontoNota())));
        }).toList();
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /** 3 → "3"; 1.500 → "1,5". */
    private static String quantidade(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String normalizar(String texto) {
        return TextoNormalizado.semAcentoMinusculo(texto);
    }

    /**
     * #451/RN-NOVA-20 — gasto por mês e itens mais comprados no período. Sem {@code de}/{@code ate}:
     * últimos 12 meses (do 1º dia de 11 meses atrás até hoje).
     */
    public ClienteGraficosResponse graficos(UUID clienteId, LocalDate de, LocalDate ate) {
        return graficos(clienteId, PapelCadastro.CLIENTE, de, ate);
    }

    /**
     * #587/RN-NOVA-36 (adendo 2) — {@code papel=FORNECEDOR}: compras CONFIRMADAS por mês (só a parte do
     * fornecedor nas compras com vários) e os 10 insumos com mais valor pago a ele no período
     * ({@code tipo = INSUMO}).
     */
    public ClienteGraficosResponse graficos(UUID clienteId, PapelCadastro papel, LocalDate de, LocalDate ate) {
        UUID usuarioId = validarCadastro(clienteId);
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : YearMonth.from(fim).minusMonths(11).atDay(1);
        if (inicio.isAfter(fim)) {
            throw BusinessException.explicado("Período inválido", "A data inicial não pode ser depois da data final.",
                    "O período vai da data inicial até a data final.",
                    "Troque as datas de lugar (ex.: de 01/09/2026 até 30/09/2026).");
        }
        if (papel == PapelCadastro.FORNECEDOR) {
            return graficosFornecedor(clienteId, usuarioId, inicio, fim);
        }

        List<Pedido> noPeriodo = comprasComItens(
                orcamentoRepository.findByClienteIdAndUsuarioIdAndDeletedAtIsNull(clienteId, usuarioId),
                vendaCaixaRepository.findByClienteIdAndUsuarioId(clienteId, usuarioId)).stream()
                .filter(p -> {
                    LocalDate d = p.resumo().dataCompra().toLocalDate();
                    return !d.isBefore(inicio) && !d.isAfter(fim);
                }).toList();

        Map<YearMonth, BigDecimal> porMes = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
            porMes.put(m, BigDecimal.ZERO);
        }
        noPeriodo.forEach(p -> porMes.merge(YearMonth.from(p.resumo().dataCompra()), p.resumo().valor(), BigDecimal::add));

        List<ClienteGraficosResponse.GastoMensal> gasto = porMes.entrySet().stream()
                .map(e -> new ClienteGraficosResponse.GastoMensal(e.getKey().atDay(1), e.getValue()))
                .toList();
        List<ItemCompradoResponse> ranking = rankingItens(noPeriodo);
        return new ClienteGraficosResponse(inicio, fim, gasto,
                ranking.subList(0, Math.min(MAX_ITENS_GRAFICO, ranking.size())));
    }

    private ClienteGraficosResponse graficosFornecedor(UUID fornecedorId, UUID usuarioId, LocalDate inicio, LocalDate fim) {
        Map<YearMonth, BigDecimal> porMes = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
            porMes.put(m, BigDecimal.ZERO);
        }
        Map<UUID, ItemCompradoResponse> porInsumo = new LinkedHashMap<>();
        linhasDoFornecedor(fornecedorId, usuarioId).forEach((compra, linhas) -> {
            if (compra.getStatus() != StatusCompra.CONFIRMADA
                    || compra.getDataCompra().isBefore(inicio) || compra.getDataCompra().isAfter(fim)) {
                return;
            }
            porMes.merge(YearMonth.from(compra.getDataCompra()), CompraMapper.total(linhas), BigDecimal::add);
            for (CompraItem l : linhas) {
                BigDecimal qtd = l.getQuantidade() != null ? l.getQuantidade() : BigDecimal.ZERO;
                BigDecimal valor = l.getPrecoTotal() != null ? l.getPrecoTotal() : BigDecimal.ZERO;
                porInsumo.merge(l.getInsumo().getId(),
                        new ItemCompradoResponse(l.getInsumo().getId(), "INSUMO", l.getInsumo().getNome(), qtd, valor),
                        (a, b) -> new ItemCompradoResponse(a.id(), a.tipo(), a.nome(), a.quantidade().add(b.quantidade()),
                                a.valor().add(b.valor())));
            }
        });
        List<ClienteGraficosResponse.GastoMensal> gasto = porMes.entrySet().stream()
                .map(e -> new ClienteGraficosResponse.GastoMensal(e.getKey().atDay(1), e.getValue()))
                .toList();
        List<ItemCompradoResponse> ranking = porInsumo.values().stream()
                .sorted(Comparator.comparing(ItemCompradoResponse::valor).reversed()
                        .thenComparing(ItemCompradoResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_ITENS_GRAFICO).toList();
        return new ClienteGraficosResponse(inicio, fim, gasto, ranking);
    }

    // ---------------------------------------------------------------------------------------------

    private List<Pedido> comprasComItens(List<Orcamento> orcamentos, List<VendaCaixa> vendas) {
        List<Orcamento> entregues = orcamentos.stream()
                .filter(o -> o.getStatus() == StatusOrcamento.ENTREGUE && o.getDataEntrega() != null).toList();
        List<VendaCaixa> concluidas = vendas.stream()
                .filter(v -> v.getStatus() == StatusVendaCaixa.CONCLUIDA).toList();

        Map<UUID, List<OrcamentoItem>> itensOrc = entregues.isEmpty() ? Map.of()
                : orcamentoItemRepository.findByOrcamentoIdIn(entregues.stream().map(Orcamento::getId).toList())
                        .stream().collect(Collectors.groupingBy(i -> i.getOrcamento().getId()));
        Map<UUID, List<VendaCaixaItem>> itensVenda = concluidas.isEmpty() ? Map.of()
                : vendaCaixaItemRepository.findByVendaCaixaIdIn(concluidas.stream().map(VendaCaixa::getId).toList())
                        .stream().collect(Collectors.groupingBy(i -> i.getVendaCaixa().getId()));

        List<Pedido> pedidos = new ArrayList<>();
        entregues.forEach(o -> pedidos.add(new Pedido(resumo(o),
                itensOrc.getOrDefault(o.getId(), List.of()).stream()
                        .map(ClienteHistoricoService::linha).filter(Objects::nonNull).toList())));
        concluidas.forEach(v -> pedidos.add(new Pedido(resumo(v),
                itensVenda.getOrDefault(v.getId(), List.of()).stream()
                        .map(ClienteHistoricoService::linha).filter(Objects::nonNull).toList())));
        return pedidos;
    }

    private static LinhaItem linha(OrcamentoItem i) {
        return linha(i.getItemCatalogo() != null ? i.getItemCatalogo().getId() : null,
                i.getItemCatalogo() != null ? i.getItemCatalogo().getNome() : null,
                i.getProduto() != null ? i.getProduto().getId() : null,
                i.getProduto() != null ? i.getProduto().getNome() : null,
                BigDecimal.valueOf(i.getQuantidade()), i.getSubtotal());
    }

    private static LinhaItem linha(VendaCaixaItem i) {
        return linha(i.getItemCatalogo() != null ? i.getItemCatalogo().getId() : null,
                i.getItemCatalogo() != null ? i.getItemCatalogo().getNome() : null,
                i.getProduto() != null ? i.getProduto().getId() : null,
                i.getProduto() != null ? i.getProduto().getNome() : null,
                i.getQuantidade(), i.getSubtotal());
    }

    // Adendo de análise (#560): "item mais comprado" = item de catálogo OU produto avulso; componentes
    // e customizações ficam fora.
    private static LinhaItem linha(UUID catalogoId, String catalogoNome, UUID produtoId, String produtoNome,
                                   BigDecimal quantidade, BigDecimal valor) {
        if (catalogoId != null) {
            return new LinhaItem(catalogoId, "ITEM_CATALOGO", catalogoNome, quantidade, valor);
        }
        if (produtoId != null) {
            return new LinhaItem(produtoId, "PRODUTO", produtoNome, quantidade, valor);
        }
        return null;
    }

    /** Por quantidade (desc); empate: maior valor, depois nome. */
    private static List<ItemCompradoResponse> rankingItens(List<Pedido> pedidos) {
        Map<UUID, ItemCompradoResponse> agregados = new LinkedHashMap<>();
        pedidos.stream().flatMap(p -> p.itens().stream()).forEach(l -> agregados.merge(l.id(),
                new ItemCompradoResponse(l.id(), l.tipo(), l.nome(), l.quantidade(), nz(l.valor())),
                (a, b) -> new ItemCompradoResponse(a.id(), a.tipo(), a.nome(),
                        a.quantidade().add(b.quantidade()), a.valor().add(b.valor()))));
        return agregados.values().stream()
                .sorted(Comparator.comparing(ItemCompradoResponse::quantidade).reversed()
                        .thenComparing(ItemCompradoResponse::valor, Comparator.reverseOrder())
                        .thenComparing(ItemCompradoResponse::nome, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static QuantidadeValorResponse somar(List<Orcamento> orcamentos) {
        return new QuantidadeValorResponse(orcamentos.size(), orcamentos.stream()
                .map(o -> nz(o.getTotal())).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static PedidoClienteResponse resumo(Orcamento o) {
        boolean compra = o.getStatus() == StatusOrcamento.ENTREGUE && o.getDataEntrega() != null;
        return new PedidoClienteResponse(o.getId(), TIPO_ORCAMENTO,
                IdentificadorFormatter.formatar("ORC", o.getNumero()), o.getCreatedAt(),
                compra ? o.getDataEntrega() : null, o.getStatus().name(), nz(o.getTotal()), compra);
    }

    private static PedidoClienteResponse resumo(VendaCaixa v) {
        boolean compra = v.getStatus() == StatusVendaCaixa.CONCLUIDA;
        return new PedidoClienteResponse(v.getId(), TIPO_VENDA_CAIXA,
                IdentificadorFormatter.formatar("CX", v.getNumero()), v.getDataVenda(),
                compra ? v.getDataVenda() : null, v.getStatus().name(), nz(v.getTotal()), compra);
    }

    static <T> Page<T> paginar(List<T> todos, Pageable pageable) {
        int inicio = (int) Math.min(pageable.getOffset(), todos.size());
        int fim = Math.min(inicio + pageable.getPageSize(), todos.size());
        return new PageImpl<>(todos.subList(inicio, fim), pageable, todos.size());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** 404 para cadastro inexistente ou de outra usuária; aceita inativos (vínculo existente). */
    UUID validarCadastro(UUID clienteId) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        clienteRepository.findByIdAndUsuarioId(clienteId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cadastro não encontrado: " + clienteId));
        return usuarioId;
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }

    /**
     * Lado Fornecedor (RN-NOVA-19): só compras CONFIRMADAS (mesmo critério da RN-NOVA-15). Numa compra
     * com vários fornecedores, conta só a parte (linhas) deste fornecedor.
     */
    private IndicadoresFornecedorResponse indicadoresFornecedor(UUID fornecedorId, UUID usuarioId) {
        Map<Compra, List<CompraItem>> porCompra = linhasDoFornecedor(fornecedorId, usuarioId).entrySet().stream()
                .filter(e -> e.getKey().getStatus() == StatusCompra.CONFIRMADA)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        long insumosVinculados = fornecedorInsumoRepository.countByUsuarioIdAndFornecedorId(usuarioId, fornecedorId);
        if (porCompra.isEmpty()) {
            return new IndicadoresFornecedorResponse(null, BigDecimal.ZERO, null, 0, null, insumosVinculados,
                    new QuantidadeValorResponse(0, BigDecimal.ZERO));
        }

        BigDecimal total = porCompra.values().stream().map(CompraMapper::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        long n = porCompra.size();
        Compra ultima = porCompra.keySet().stream()
                .max(Comparator.comparing(Compra::getDataCompra).thenComparing(Compra::getNumero)).orElseThrow();

        Map<UUID, IndicadoresFornecedorResponse.InsumoCompradoResponse> porInsumo = new LinkedHashMap<>();
        porCompra.values().stream().flatMap(List::stream).filter(i -> i.getQuantidade() != null)
                .forEach(i -> porInsumo.merge(i.getInsumo().getId(),
                        new IndicadoresFornecedorResponse.InsumoCompradoResponse(i.getInsumo().getId(), i.getInsumo().getNome(),
                                i.getInsumo().getUnidadeMedida() != null ? i.getInsumo().getUnidadeMedida().getSigla() : null,
                                i.getQuantidade()),
                        (a, b) -> new IndicadoresFornecedorResponse.InsumoCompradoResponse(a.id(), a.nome(), a.unidade(),
                                a.quantidade().add(b.quantidade()))));
        IndicadoresFornecedorResponse.InsumoCompradoResponse maisComprado = porInsumo.values().stream()
                .max(Comparator.comparing(IndicadoresFornecedorResponse.InsumoCompradoResponse::quantidade)
                        .thenComparing(IndicadoresFornecedorResponse.InsumoCompradoResponse::nome, Comparator.reverseOrder()))
                .orElse(null);

        List<Map.Entry<Compra, List<CompraItem>>> naoPagas = porCompra.entrySet().stream()
                .filter(e -> !Boolean.TRUE.equals(e.getKey().getPago())).toList();

        return new IndicadoresFornecedorResponse(
                new IndicadoresFornecedorResponse.UltimaCompraFornecedor(ultima.getId(),
                        IdentificadorFormatter.formatar("COM", ultima.getNumero()), ultima.getDataCompra()),
                total,
                total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                n,
                maisComprado,
                insumosVinculados,
                new QuantidadeValorResponse(naoPagas.size(), naoPagas.stream().map(e -> CompraMapper.total(e.getValue()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    /** Aba Histórico, seção Fornecedor: todas as compras (qualquer status, sem excluídas). */
    public Page<CompraFornecedorResponse> historicoCompras(UUID fornecedorId, Pageable pageable) {
        UUID usuarioId = validarCadastro(fornecedorId);
        List<CompraFornecedorResponse> todas = linhasDoFornecedor(fornecedorId, usuarioId).entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Compra, List<CompraItem>> e) -> e.getKey().getDataCompra())
                        .thenComparing(e -> e.getKey().getNumero()).reversed())
                .map(e -> new CompraFornecedorResponse(e.getKey().getId(),
                        IdentificadorFormatter.formatar("COM", e.getKey().getNumero()), e.getKey().getDataCompra(),
                        e.getKey().getStatus(), CompraMapper.total(e.getValue()), Boolean.TRUE.equals(e.getKey().getPago())))
                .toList();
        return paginar(todas, pageable);
    }

    /** Compras em que o cadastro aparece (cabeçalho ou linha) → linhas dele nessa compra. */
    private Map<Compra, List<CompraItem>> linhasDoFornecedor(UUID fornecedorId, UUID usuarioId) {
        List<Compra> compras = compraRepository.findDoFornecedor(usuarioId, fornecedorId);
        if (compras.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<CompraItem>> itens = compraItemRepository.findByCompraIdIn(compras.stream().map(Compra::getId).toList())
                .stream()
                .filter(i -> i.getFornecedor() != null && i.getFornecedor().getId().equals(fornecedorId))
                .collect(Collectors.groupingBy(i -> i.getCompra().getId()));
        Map<Compra, List<CompraItem>> resultado = new LinkedHashMap<>();
        compras.forEach(c -> resultado.put(c, itens.getOrDefault(c.getId(), List.of())));
        return resultado;
    }
}
