package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteGraficosResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteIndicadoresResponse;
import com.penseprecifique.api.shared.dto.response.cliente.IndicadoresClienteResponse;
import com.penseprecifique.api.shared.dto.response.cliente.IndicadoresFornecedorResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ItemCompradoResponse;
import com.penseprecifique.api.shared.dto.response.cliente.PedidoClienteResponse;
import com.penseprecifique.api.shared.dto.response.cliente.QuantidadeValorResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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

    /**
     * #451/RN-NOVA-20 — gasto por mês e itens mais comprados no período. Sem {@code de}/{@code ate}:
     * últimos 12 meses (do 1º dia de 11 meses atrás até hoje).
     */
    public ClienteGraficosResponse graficos(UUID clienteId, LocalDate de, LocalDate ate) {
        UUID usuarioId = validarCadastro(clienteId);
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : YearMonth.from(fim).minusMonths(11).atDay(1);
        if (inicio.isAfter(fim)) {
            throw new BusinessException("A data inicial não pode ser depois da data final.");
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
                        .map(i -> linha(i.getItemCatalogo() != null ? i.getItemCatalogo().getId() : null,
                                i.getItemCatalogo() != null ? i.getItemCatalogo().getNome() : null,
                                i.getProduto() != null ? i.getProduto().getId() : null,
                                i.getProduto() != null ? i.getProduto().getNome() : null,
                                BigDecimal.valueOf(i.getQuantidade()), i.getSubtotal()))
                        .filter(Objects::nonNull).toList())));
        concluidas.forEach(v -> pedidos.add(new Pedido(resumo(v),
                itensVenda.getOrDefault(v.getId(), List.of()).stream()
                        .map(i -> linha(i.getItemCatalogo() != null ? i.getItemCatalogo().getId() : null,
                                i.getItemCatalogo() != null ? i.getItemCatalogo().getNome() : null,
                                i.getProduto() != null ? i.getProduto().getId() : null,
                                i.getProduto() != null ? i.getProduto().getNome() : null,
                                i.getQuantidade(), i.getSubtotal()))
                        .filter(Objects::nonNull).toList())));
        return pedidos;
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

    /** Lado Fornecedor (RN-NOVA-19): compras CONFIRMADAS — preenchido junto com o módulo compra/. */
    private IndicadoresFornecedorResponse indicadoresFornecedor(UUID fornecedorId, UUID usuarioId) {
        return IndicadoresFornecedorResponse.vazio();
    }
}
