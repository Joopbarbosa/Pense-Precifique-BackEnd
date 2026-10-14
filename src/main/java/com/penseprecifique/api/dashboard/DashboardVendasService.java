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
import java.util.stream.Collectors;

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
                meses, origens, produtosMaisVendidos(doPeriodo), formasPagamento(doPeriodo));
    }

    /**
     * Aba Vendas. {@code somenteQueContam}: só orçamento ENTREGUE e venda CONCLUÍDA (é o que o clique num número ou gráfico abre);
     * sem ele, todas as linhas. Filtros por origem, status (vários somam como OU) e busca por identificador ou cliente.
     */
    public ListaVendasResponse lista(LocalDate de, LocalDate ate, TipoVendaCmv origem, Collection<String> status, String busca,
                                     boolean somenteQueContam, Pageable pageable) {
        PeriodoDashboard p = PeriodoDashboard.de(de, ate);
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new BusinessException("Use uma página de 1 a 100 vendas.");
        }
        String termo = normalizar(busca);
        List<VendasService.LinhaLista> filtradas = vendasService.todas(usuarioId(), p.inicio(), p.fim()).stream()
                .filter(l -> origem == null || l.origem() == origem)
                .filter(l -> status == null || status.isEmpty() || status.contains(l.status()))
                .filter(l -> !somenteQueContam || l.contaNosNumeros())
                .filter(l -> termo.isEmpty() || normalizar(l.identificador()).contains(termo)
                        || normalizar(l.cliente()).contains(termo))
                .sorted(Comparator.comparing(VendasService.LinhaLista::data).reversed()
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
        return new ListaVendasResponse(pag, total, filtradas.size());
    }

    // ---------------------------------------------------------------------------------------------

    private static List<VendasService.Venda> entre(List<VendasService.Venda> vendas, LocalDate de, LocalDate ate) {
        return vendas.stream().filter(v -> !v.data().isBefore(de) && !v.data().isAfter(ate)).toList();
    }

    private static BigDecimal faturamento(List<VendasService.Venda> vendas) {
        return vendas.stream().map(VendasService.Venda::faturamento).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal ticket(BigDecimal faturamento, int quantidade) {
        return quantidade == 0 ? null : faturamento.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    /** Mais vendidos pela quantidade; empate pelo faturamento e depois pelo nome (resultado igual em qualquer tela). */
    private static List<DashboardVendasResponse.ProdutoVendido> produtosMaisVendidos(List<VendasService.Venda> vendas) {
        Map<String, DashboardVendasResponse.ProdutoVendido> soma = new LinkedHashMap<>();
        for (VendasService.Venda v : vendas) {
            for (VendasService.LinhaProduto l : v.linhas()) {
                String chave = l.tipo() + "|" + (l.id() != null ? l.id() : l.nome());
                soma.merge(chave, new DashboardVendasResponse.ProdutoVendido(l.id(), l.tipo(), l.nome(), l.quantidade(), l.faturamento()),
                        (a, b) -> new DashboardVendasResponse.ProdutoVendido(a.id(), a.tipo(), a.nome(),
                                a.quantidade().add(b.quantidade()), a.faturamento().add(b.faturamento())));
            }
        }
        return soma.values().stream()
                .sorted(Comparator.comparing(DashboardVendasResponse.ProdutoVendido::quantidade).reversed()
                        .thenComparing(DashboardVendasResponse.ProdutoVendido::faturamento, Comparator.reverseOrder())
                        .thenComparing(DashboardVendasResponse.ProdutoVendido::nome))
                .limit(TOP).toList();
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
