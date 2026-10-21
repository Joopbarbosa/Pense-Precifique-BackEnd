package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.enums.TipoVendaCmv;
import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * #719/RN-NOVA-17 (V0.17.0, DT-NOVA-8 e 10) — Dashboard de Vendas: aba Gráficos (números, meses, origem, produtos mais vendidos e
 * formas de pagamento) e aba Vendas (todas as vendas e orçamentos, de qualquer status). Calculado de forma síncrona, sem cache, sobre
 * a fonte única {@link VendasService}; desempate dos rankings e comparativos ficam aqui, para valer igual em qualquer tela.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardVendasService {

    private static final int TOP = 10;
    private static final String NAO_INFORMADA = "Não informada";

    private final VendasService vendasService;
    private final UsuarioRepository usuarioRepository;

    public DashboardVendasResponse dashboard(LocalDate de, LocalDate ate) {
        UUID usuarioId = usuarioId();
        PeriodoDashboard p = PeriodoDashboard.de(de, ate);
        List<VendasService.Venda> todas = vendasService.vendas(usuarioId, p.inicioGeral(), p.fim());
        List<VendasService.Venda> doPeriodo = entre(todas, p.inicio(), p.fim());
        List<VendasService.Venda> doAnterior = entre(todas, p.inicioAnterior(), p.fimAnterior());

        BigDecimal faturamento = faturamento(doPeriodo);
        BigDecimal faturamentoAnterior = faturamento(doAnterior);
        BigDecimal ticket = ticket(faturamento, doPeriodo.size());
        BigDecimal ticketAnterior = ticket(faturamentoAnterior, doAnterior.size());

        List<DashboardVendasResponse.Mes> meses = new ArrayList<>();
        for (YearMonth m = p.primeiroMes(); !m.isAfter(YearMonth.from(p.fim())); m = m.plusMonths(1)) {
            List<VendasService.Venda> doMes = entre(todas, m.atDay(1), m.atEndOfMonth());
            meses.add(new DashboardVendasResponse.Mes(m.atDay(1), faturamento(doMes), doMes.size()));
        }

        List<DashboardVendasResponse.Origem> origens = new ArrayList<>();
        for (TipoVendaCmv tipo : TipoVendaCmv.values()) {
            List<VendasService.Venda> daOrigem = doPeriodo.stream().filter(v -> v.tipo() == tipo).toList();
            origens.add(new DashboardVendasResponse.Origem(tipo.name(), faturamento(daOrigem), daOrigem.size()));
        }

        return new DashboardVendasResponse(p.inicio(), p.fim(), p.inicioAnterior(), p.fimAnterior(),
                DashboardNumero.de(faturamento, faturamentoAnterior),
                DashboardNumero.de(BigDecimal.valueOf(doPeriodo.size()), BigDecimal.valueOf(doAnterior.size())),
                DashboardNumero.de(ticket, ticketAnterior),
                meses, origens, produtosMaisVendidos(doPeriodo), todosProdutosVendidos(doPeriodo), formasPagamento(doPeriodo));
    }

    /**
     * Aba Vendas. {@code somenteQueContam}: só orçamento ENTREGUE e venda CONCLUÍDA (é o que o clique num número ou gráfico abre);
     * sem ele, todas as linhas. Filtros por origem, status (vários somam como OU) e busca por identificador ou cliente.
     */
    public ListaVendasResponse lista(LocalDate de, LocalDate ate, TipoVendaCmv origem, Collection<String> status, String busca,
                                     boolean somenteQueContam, Pageable pageable) {
        return lista(de, ate, origem, status, busca, somenteQueContam, null, null, pageable);
    }

    /**
     * {@code produtoId} (id do produto/Catálogo, ou o nome quando a linha não tem id) e {@code forma} (forma de pagamento, inclusive
     * "Não informada") abrem as vendas que formam aquele ranking; valem só sobre vendas que contam nos números (RN-NOVA-12).
     */
    public ListaVendasResponse lista(LocalDate de, LocalDate ate, TipoVendaCmv origem, Collection<String> status, String busca,
                                     boolean somenteQueContam, String produtoId, String forma, Pageable pageable) {
        PeriodoDashboard p = PeriodoDashboard.de(de, ate);
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new BusinessException("Use uma página de 1 a 100 vendas.");
        }
        String termo = normalizar(busca);
        boolean porRanking = (produtoId != null && !produtoId.isBlank()) || (forma != null && !forma.isBlank());
        List<VendasService.Venda> vendasDoPeriodo = porRanking ? vendasService.vendas(usuarioId(), p.inicio(), p.fim()) : List.of();
        java.util.Set<String> queFormam = porRanking ? vendasQueFormam(vendasDoPeriodo, produtoId, forma) : null;
        List<VendasService.LinhaLista> filtradas = vendasService.todas(usuarioId(), p.inicio(), p.fim()).stream()
                .filter(l -> queFormam == null || queFormam.contains(l.origem() + "|" + l.id()))
                .filter(l -> origem == null || l.origem() == origem)
                .filter(l -> status == null || status.isEmpty() || status.contains(l.status()))
                .filter(l -> !somenteQueContam || l.contaNosNumeros())
                .filter(l -> termo.isEmpty() || normalizar(l.identificador()).contains(termo)
                        || normalizar(l.cliente()).contains(termo))
                .sorted(comparadorLista(pageable).thenComparing(Comparator.comparing(VendasService.LinhaLista::data).reversed())
                        .thenComparing(VendasService.LinhaLista::identificador).thenComparing(VendasService.LinhaLista::id))
                .toList();
        BigDecimal total = filtradas.stream().map(VendasService.LinhaLista::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        int inicio = (int) Math.min(pageable.getOffset(), filtradas.size());
        int fim = Math.min(inicio + pageable.getPageSize(), filtradas.size());
        List<ListaVendasResponse.Linha> pagina = filtradas.subList(inicio, fim).stream()
                .map(l -> new ListaVendasResponse.Linha(l.id(), l.origem().name(), l.identificador(), l.data(), l.cliente(),
                        l.itens(), l.total(), l.status(), l.contaNosNumeros()))
                .toList();
        Page<ListaVendasResponse.Linha> pag = new PageImpl<>(pagina, pageable, filtradas.size());
        BigDecimal unidades = produtoId == null || produtoId.isBlank() ? null : unidadesDoProduto(vendasDoPeriodo, filtradas, produtoId);
        return new ListaVendasResponse(pag, total, filtradas.size(), unidades);
    }

    /** #988: soma, nas vendas da lista, das unidades do produto clicado (só as linhas dele, não as dos outros produtos da venda). */
    private static BigDecimal unidadesDoProduto(List<VendasService.Venda> vendas, List<VendasService.LinhaLista> listadas, String produtoId) {
        java.util.Set<String> chaves = listadas.stream().map(l -> l.origem() + "|" + l.id()).collect(java.util.stream.Collectors.toSet());
        return vendas.stream()
                .filter(v -> chaves.contains(v.tipo() + "|" + v.id()))
                .flatMap(v -> v.linhas().stream())
                .filter(l -> produtoId.equals(String.valueOf(l.id())) || produtoId.equals(l.nome()))
                .map(VendasService.LinhaProduto::quantidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------------------------------------

    private java.util.Set<String> vendasQueFormam(List<VendasService.Venda> vendas, String produtoId, String forma) {
        return vendas.stream()
                .filter(v -> produtoId == null || produtoId.isBlank() || v.linhas().stream()
                        .anyMatch(l -> produtoId.equals(String.valueOf(l.id())) || produtoId.equals(l.nome())))
                .filter(v -> forma == null || forma.isBlank()
                        || (v.pagamentos().isEmpty() ? NAO_INFORMADA.equals(forma) : v.pagamentos().stream().anyMatch(pg -> forma.equals(pg.forma()))))
                .map(v -> v.tipo() + "|" + v.id())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static List<VendasService.Venda> entre(List<VendasService.Venda> vendas, LocalDate de, LocalDate ate) {
        return vendas.stream().filter(v -> !v.data().isBefore(de) && !v.data().isAfter(ate)).toList();
    }

    private static BigDecimal faturamento(List<VendasService.Venda> vendas) {
        return vendas.stream().map(VendasService.Venda::faturamento).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal ticket(BigDecimal faturamento, int quantidade) {
        return quantidade == 0 ? null : faturamento.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    /** Top 10 de produtos vendidos (RN-NOVA-23): mesma ordem da lista completa, cortada no 10º. */
    private static List<DashboardVendasResponse.ProdutoVendido> produtosMaisVendidos(List<VendasService.Venda> vendas) {
        return todosProdutosVendidos(vendas).stream().limit(TOP).toList();
    }

    /**
     * Produtos vendidos ordenados pela quantidade (maior primeiro); empate pelo nome, sem diferenciar maiúsculas e
     * acentos, depois pelo tipo e pelo id (RN-NOVA-23 — o desempate é o mesmo em qualquer tela).
     */
    private static List<DashboardVendasResponse.ProdutoVendido> todosProdutosVendidos(List<VendasService.Venda> vendas) {
        Map<String, DashboardVendasResponse.ProdutoVendido> soma = new LinkedHashMap<>();
        for (VendasService.Venda v : vendas) {
            for (VendasService.LinhaProduto l : v.linhas()) {
                String chave = l.tipo() + "|" + (l.id() != null ? l.id() : l.nome());
                soma.merge(chave, new DashboardVendasResponse.ProdutoVendido(l.id(), l.tipo(), l.nome(), l.marca(), l.quantidade(), l.faturamento()),
                        (a, b) -> new DashboardVendasResponse.ProdutoVendido(a.id(), a.tipo(), a.nome(), a.marca(),
                                a.quantidade().add(b.quantidade()), a.faturamento().add(b.faturamento())));
            }
        }
        return soma.values().stream()
                .sorted(Comparator.comparing(DashboardVendasResponse.ProdutoVendido::quantidade).reversed()
                        .thenComparing(p -> normalizar(p.nome()))
                        .thenComparing(DashboardVendasResponse.ProdutoVendido::tipo)
                        .thenComparing(p -> p.id() == null ? "" : p.id().toString()))
                .toList();
    }

    private static List<DashboardVendasResponse.FormaPagamento> formasPagamento(List<VendasService.Venda> vendas) {
        Map<String, BigDecimal> valor = new LinkedHashMap<>();
        Map<String, Long> quantidade = new LinkedHashMap<>();
        for (VendasService.Venda v : vendas) {
            if (v.pagamentos().isEmpty()) {
                valor.merge(NAO_INFORMADA, v.faturamento(), BigDecimal::add);
                quantidade.merge(NAO_INFORMADA, 1L, Long::sum);
                continue;
            }
            for (String forma : v.pagamentos().stream().map(VendasService.Pagamento::forma).distinct().toList()) {
                quantidade.merge(forma, 1L, Long::sum);
            }
            for (VendasService.Pagamento pg : v.pagamentos()) {
                valor.merge(pg.forma(), pg.valor(), BigDecimal::add);
            }
        }
        return valor.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new DashboardVendasResponse.FormaPagamento(e.getKey(), e.getValue(), quantidade.getOrDefault(e.getKey(), 0L)))
                .toList();
    }

    /**
     * V0.17.0 #971 (DT-NOVA-18): ordenação pedida pela tabela (identificador, data, cliente, total ou status), com
     * sentido. Sem ordenação pedida, a ordem é a de sempre (data mais recente primeiro). Campo fora da lista = BLOQUEIO.
     */
    private static Comparator<VendasService.LinhaLista> comparadorLista(Pageable pageable) {
        Comparator<VendasService.LinhaLista> nada = (a, b) -> 0;
        Comparator<VendasService.LinhaLista> criterio = nada;
        for (org.springframework.data.domain.Sort.Order ordem : pageable.getSort()) {
            Comparator<VendasService.LinhaLista> campo = switch (ordem.getProperty()) {
                case "identificador" -> Comparator.comparing((VendasService.LinhaLista l) -> prefixoIdentificador(l.identificador()))
                        .thenComparingLong(l -> numeroIdentificador(l.identificador()));
                case "data" -> Comparator.comparing(VendasService.LinhaLista::data);
                case "cliente" -> Comparator.comparing(l -> normalizar(l.cliente()));
                case "total" -> Comparator.comparing(VendasService.LinhaLista::total);
                case "status" -> Comparator.comparing(l -> normalizar(l.status()));
                default -> throw new BusinessException("Ordenação inválida para as vendas.");
            };
            criterio = criterio.thenComparing(ordem.isDescending() ? campo.reversed() : campo);
        }
        return criterio;
    }

    private static String prefixoIdentificador(String identificador) {
        return identificador == null ? "" : identificador.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private static long numeroIdentificador(String identificador) {
        String digitos = identificador == null ? "" : identificador.replaceAll("\\D", "");
        return digitos.isEmpty() ? 0L : Long.parseLong(digitos.length() > 18 ? digitos.substring(0, 18) : digitos);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }
}
