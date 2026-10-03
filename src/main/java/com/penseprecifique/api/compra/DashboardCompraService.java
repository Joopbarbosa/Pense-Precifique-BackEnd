package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.response.compra.DashboardComprasResponse;
import com.penseprecifique.api.shared.dto.response.cliente.QuantidadeValorResponse;
import com.penseprecifique.api.shared.dto.response.compra.EvolucaoPrecoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #548/RN-NOVA-15 e #577/RN-NOVA-29 (V0.15.0, DT-NOVA-9/18) — painel de compras do período e evolução
 * do preço pago. Agregação
 * síncrona ao vivo sobre as linhas de compras CONFIRMADAS (rascunhos e canceladas nunca entram).
 * Valores usam o preço PAGO da linha, nunca o custo médio do insumo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardCompraService {

    static final int MAX_INSUMOS_GRAFICO = 5;
    static final int TOP = 10;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final CompraItemRepository compraItemRepository;
    private final InsumoRepository insumoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CompraMapper compraMapper;
    private final CmvService cmvService;

    private static final Comparator<CompraItem> CRONOLOGICA = Comparator
            .comparing((CompraItem i) -> i.getCompra().getDataCompra())
            .thenComparing(i -> i.getCompra().getNumero())
            .thenComparing(CompraItem::getOrdem);

    /**
     * #577/RN-NOVA-29 — painel do período. Sem {@code de}/{@code ate}: mês atual (dia 1 até hoje).
     * Período anterior: começando no dia 1, os mesmos N meses de calendário imediatamente antes (mês
     * atual → mês passado inteiro); senão, a mesma quantidade de dias imediatamente antes.
     */
    public DashboardComprasResponse dashboard(LocalDate de, LocalDate ate) {
        UUID usuarioId = usuarioId();
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : fim.withDayOfMonth(1);
        if (inicio.isAfter(fim)) {
            throw BusinessException.explicado("Período inválido", "A data inicial não pode ser depois da data final.",
                    "O período vai da data inicial até a data final.",
                    "Troque as datas de lugar (ex.: de 01/09/2026 até 30/09/2026).");
        }
        LocalDate fimAnterior = inicio.minusDays(1);
        LocalDate inicioAnterior = inicio.getDayOfMonth() == 1
                ? inicio.minusMonths(ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) + 1)
                : inicio.minusDays(ChronoUnit.DAYS.between(inicio, fim) + 1);
        YearMonth primeiroMes = YearMonth.from(inicio).isBefore(YearMonth.from(fim).minusMonths(5))
                ? YearMonth.from(inicio) : YearMonth.from(fim).minusMonths(5);
        LocalDate inicioGeral = primeiroMes.atDay(1).isBefore(inicioAnterior) ? primeiroMes.atDay(1) : inicioAnterior;

        List<CompraItem> linhas = compraItemRepository.findPorStatus(usuarioId, StatusCompra.CONFIRMADA);
        List<CompraItem> doPeriodo = entre(linhas, inicio, fim);
        List<CompraItem> doAnterior = entre(linhas, inicioAnterior, fimAnterior);

        BigDecimal gasto = somar(doPeriodo);
        BigDecimal gastoAnterior = somar(doAnterior);
        long compras = contarCompras(doPeriodo);
        long comprasAnterior = contarCompras(doAnterior);
        BigDecimal ticket = compras == 0 ? null : gasto.divide(BigDecimal.valueOf(compras), 2, RoundingMode.HALF_UP);
        BigDecimal ticketAnterior = comprasAnterior == 0 ? null
                : gastoAnterior.divide(BigDecimal.valueOf(comprasAnterior), 2, RoundingMode.HALF_UP);

        BigDecimal desconto = descontos(doPeriodo);
        BigDecimal cheio = cheio(doPeriodo);
        BigDecimal descontoAnterior = descontos(doAnterior);

        List<CmvService.Venda> vendas = cmvService.vendas(usuarioId, inicioGeral, fim);
        List<CmvService.Venda> vendasPeriodo = vendasEntre(vendas, inicio, fim);
        List<CmvService.Venda> vendasAnterior = vendasEntre(vendas, inicioAnterior, fimAnterior);
        BigDecimal cmv = somarCmv(vendasPeriodo);
        BigDecimal faturamento = somarFaturamento(vendasPeriodo);
        BigDecimal cmvAnterior = somarCmv(vendasAnterior);

        List<DashboardComprasResponse.InsumoVariacao> subiram = insumosQueMaisSubiram(doPeriodo);

        List<DashboardComprasResponse.Mes> meses = new ArrayList<>();
        for (YearMonth m = primeiroMes; !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
            LocalDate a = m.atDay(1);
            LocalDate b = m.atEndOfMonth();
            List<CmvService.Venda> vm = vendasEntre(vendas, a, b);
            BigDecimal fat = somarFaturamento(vm);
            BigDecimal c = somarCmv(vm);
            // #599/RN-NOVA-37 (adendo 2) — mês sem faturamento = 0% no gráfico (linha sem buraco); o
            // número CMV % do período continua "—" sem faturamento (RN-NOVA-27).
            BigDecimal pct = percentual(c, fat);
            meses.add(new DashboardComprasResponse.Mes(a, somar(entre(linhas, a, b)), c, fat,
                    pct != null ? pct : BigDecimal.ZERO.setScale(2)));
        }

        List<Compra> naoPagas = linhas.stream().map(CompraItem::getCompra).distinct()
                .filter(c -> !Boolean.TRUE.equals(c.getPago())).toList();
        BigDecimal valorNaoPago = linhas.stream().filter(i -> !Boolean.TRUE.equals(i.getCompra().getPago()))
                .map(CompraItem::getPrecoTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardComprasResponse(inicio, fim, inicioAnterior, fimAnterior,
                numero(gasto, gastoAnterior),
                numero(BigDecimal.valueOf(compras), BigDecimal.valueOf(comprasAnterior)),
                numero(ticket, ticketAnterior),
                new DashboardComprasResponse.Economia(desconto, cheio, percentual(desconto, cheio), descontoAnterior,
                        variacaoEntre(descontoAnterior, desconto)),
                new DashboardComprasResponse.Cmv(cmv, faturamento, percentual(cmv, faturamento),
                        vendasPeriodo.stream().map(CmvService.Venda::estimado).reduce(BigDecimal.ZERO, BigDecimal::add),
                        vendasPeriodo.stream().filter(CmvService.Venda::semCusto).count(),
                        cmvAnterior, percentual(cmvAnterior, somarFaturamento(vendasAnterior)),
                        variacaoEntre(cmvAnterior, cmv)),
                new QuantidadeValorResponse(naoPagas.size(), valorNaoPago),
                subiram.isEmpty() ? null : subiram.get(0),
                meses,
                subiram,
                fornecedoresPorGasto(doPeriodo),
                fornecedoresPorDesconto(doPeriodo, true),
                fornecedoresPorDesconto(doPeriodo, false));
    }

    /**
     * Até 5 insumos; período default = últimos 3 meses. Um ponto por linha confirmada com preço pago.
     */
    public EvolucaoPrecoResponse evolucaoPreco(List<UUID> insumoIds, LocalDate de, LocalDate ate) {
        UUID usuarioId = usuarioId();
        Set<UUID> ids = new LinkedHashSet<>(insumoIds != null ? insumoIds : List.of());
        if (ids.isEmpty()) {
            throw new BusinessException("Escolha pelo menos um insumo.");
        }
        if (ids.size() > MAX_INSUMOS_GRAFICO) {
            throw new BusinessException("Escolha no máximo 5 insumos.");
        }
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : fim.minusMonths(3);
        if (inicio.isAfter(fim)) {
            throw BusinessException.explicado("Período inválido", "A data inicial não pode ser depois da data final.",
                    "O período vai da data inicial até a data final.",
                    "Troque as datas de lugar (ex.: de 01/09/2026 até 30/09/2026).");
        }

        Map<UUID, Insumo> insumos = new HashMap<>();
        for (UUID id : ids) {
            insumos.put(id, insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + id)));
        }
        Map<UUID, List<CompraItem>> porInsumo = compraItemRepository.findPorInsumosEStatus(usuarioId, ids, StatusCompra.CONFIRMADA)
                .stream()
                .filter(i -> i.getPrecoUnitarioPago() != null)
                .filter(i -> !i.getCompra().getDataCompra().isBefore(inicio) && !i.getCompra().getDataCompra().isAfter(fim))
                .sorted(CRONOLOGICA)
                .collect(Collectors.groupingBy(i -> i.getInsumo().getId()));

        List<EvolucaoPrecoResponse.Serie> series = new ArrayList<>();
        for (UUID id : ids) {
            List<CompraItem> pontos = porInsumo.getOrDefault(id, List.of());
            BigDecimal base = pontos.isEmpty() ? null : pontos.get(0).getPrecoUnitarioPago();
            series.add(new EvolucaoPrecoResponse.Serie(compraMapper.toRef(insumos.get(id)), pontos.stream()
                    .map(i -> new EvolucaoPrecoResponse.Ponto(i.getCompra().getDataCompra(), i.getCompra().getId(),
                            IdentificadorFormatter.formatar("COM", i.getCompra().getNumero()), i.getPrecoUnitarioPago(),
                            i.getQuantidade(), i.getFornecedor() != null ? i.getFornecedor().getNome() : null,
                            variacao(base, i.getPrecoUnitarioPago()),
                            i.getFornecedor() != null ? i.getFornecedor().getId() : null))
                    .toList()));
        }
        return new EvolucaoPrecoResponse(inicio, fim, series);
    }

    // ---------------------------------------------------------------------------------------------

    private static BigDecimal somar(List<CompraItem> linhas) {
        return linhas.stream().map(CompraItem::getPrecoTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<CompraItem> entre(List<CompraItem> linhas, LocalDate de, LocalDate ate) {
        return linhas.stream().filter(i -> {
            LocalDate d = i.getCompra().getDataCompra();
            return !d.isBefore(de) && !d.isAfter(ate);
        }).toList();
    }

    private static List<CmvService.Venda> vendasEntre(List<CmvService.Venda> vendas, LocalDate de, LocalDate ate) {
        return vendas.stream().filter(v -> !v.data().isBefore(de) && !v.data().isAfter(ate)).toList();
    }

    private static long contarCompras(List<CompraItem> linhas) {
        return linhas.stream().map(i -> i.getCompra().getId()).distinct().count();
    }

    /** #576 — desconto de linha + parte da nota. */
    private static BigDecimal descontos(List<CompraItem> linhas) {
        return linhas.stream().map(i -> nz(i.getDescontoLinha()).add(nz(i.getDescontoNota())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Preço cheio (compras antigas, sem o campo: o próprio preço pago). */
    private static BigDecimal cheio(List<CompraItem> linhas) {
        return linhas.stream().map(i -> i.getPrecoCheio() != null ? i.getPrecoCheio() : nz(i.getPrecoTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal somarCmv(List<CmvService.Venda> vendas) {
        return vendas.stream().map(CmvService.Venda::cmv).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal somarFaturamento(List<CmvService.Venda> vendas) {
        return vendas.stream().map(CmvService.Venda::faturamento).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** parte ÷ todo × 100, 2 casas; nulo sem base. */
    private static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (parte == null || todo == null || todo.signum() == 0) {
            return null;
        }
        return parte.multiply(CEM).divide(todo, 2, RoundingMode.HALF_UP);
    }

    private static DashboardComprasResponse.Numero numero(BigDecimal atual, BigDecimal anterior) {
        return new DashboardComprasResponse.Numero(atual, anterior, variacaoEntre(anterior, atual));
    }

    private static BigDecimal variacaoEntre(BigDecimal anterior, BigDecimal atual) {
        return anterior == null || atual == null ? null : variacao(anterior, atual);
    }

    /** Variação entre o primeiro e o último preço pago no período (2+ compras), só aumentos, 10 maiores. */
    private List<DashboardComprasResponse.InsumoVariacao> insumosQueMaisSubiram(List<CompraItem> linhas) {
        Map<UUID, List<CompraItem>> porInsumo = linhas.stream()
                .filter(i -> i.getPrecoUnitarioPago() != null)
                .sorted(CRONOLOGICA)
                .collect(Collectors.groupingBy(i -> i.getInsumo().getId()));
        List<DashboardComprasResponse.InsumoVariacao> lista = new ArrayList<>();
        for (List<CompraItem> serie : porInsumo.values()) {
            if (serie.size() < 2) {
                continue;
            }
            CompraItem primeiro = serie.get(0);
            CompraItem ultimo = serie.get(serie.size() - 1);
            BigDecimal v = variacao(primeiro.getPrecoUnitarioPago(), ultimo.getPrecoUnitarioPago());
            if (v != null && v.signum() > 0) {
                lista.add(new DashboardComprasResponse.InsumoVariacao(compraMapper.toRef(primeiro.getInsumo()),
                        primeiro.getPrecoUnitarioPago(), primeiro.getCompra().getDataCompra(),
                        ultimo.getPrecoUnitarioPago(), ultimo.getCompra().getDataCompra(), v));
            }
        }
        return lista.stream()
                .sorted(Comparator.comparing(DashboardComprasResponse.InsumoVariacao::variacaoPercentual).reversed()
                        .thenComparing(x -> x.insumo().nome()))
                .limit(TOP)
                .toList();
    }

    /** Por fornecedor da linha: soma do preço pago e compras distintas; 10 maiores por valor. */
    private List<DashboardComprasResponse.FornecedorGasto> fornecedoresPorGasto(List<CompraItem> linhas) {
        Map<UUID, List<CompraItem>> porFornecedor = linhas.stream().filter(i -> i.getFornecedor() != null)
                .collect(Collectors.groupingBy(i -> i.getFornecedor().getId()));
        return porFornecedor.values().stream()
                .map(ls -> new DashboardComprasResponse.FornecedorGasto(compraMapper.toRef(ls.get(0).getFornecedor()),
                        somar(ls), contarCompras(ls)))
                .sorted(Comparator.comparing(DashboardComprasResponse.FornecedorGasto::valor).reversed()
                        .thenComparing(x -> x.fornecedor().nome()))
                .limit(TOP)
                .toList();
    }

    /** Só fornecedores com algum desconto; 10 maiores por % (descontos ÷ cheio) ou por R$. */
    private List<DashboardComprasResponse.FornecedorDesconto> fornecedoresPorDesconto(List<CompraItem> linhas, boolean porPercentual) {
        Map<UUID, List<CompraItem>> porFornecedor = linhas.stream().filter(i -> i.getFornecedor() != null)
                .collect(Collectors.groupingBy(i -> i.getFornecedor().getId()));
        Comparator<DashboardComprasResponse.FornecedorDesconto> ordem = porPercentual
                ? Comparator.comparing(DashboardComprasResponse.FornecedorDesconto::percentual)
                : Comparator.comparing(DashboardComprasResponse.FornecedorDesconto::desconto);
        return porFornecedor.values().stream()
                .map(ls -> {
                    BigDecimal d = descontos(ls);
                    BigDecimal c = cheio(ls);
                    return new DashboardComprasResponse.FornecedorDesconto(compraMapper.toRef(ls.get(0).getFornecedor()),
                            d, c, percentual(d, c));
                })
                .filter(x -> x.desconto().signum() > 0)
                .sorted(ordem.reversed().thenComparing(x -> x.fornecedor().nome()))
                .limit(TOP)
                .toList();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** (atual − base) ÷ base × 100, 2 casas. */
    static BigDecimal variacao(BigDecimal base, BigDecimal atual) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return atual.subtract(base).multiply(CEM).divide(base, 2, RoundingMode.HALF_UP);
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }
}
