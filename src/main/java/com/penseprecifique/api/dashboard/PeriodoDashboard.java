package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.validation.FaixaDataPermitida;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * #719/#607 (V0.17.0, DT-NOVA-10) — período dos dashboards e o período anterior de comparação, com a mesma regra do dashboard de
 * Compras (#577): sem datas, o mês atual; começando no dia 1, os mesmos N meses de calendário imediatamente antes; senão, a
 * mesma quantidade de dias imediatamente antes. {@code inicioGeral} cobre o anterior e os (no mínimo) 6 meses do gráfico.
 */
public record PeriodoDashboard(LocalDate inicio, LocalDate fim, LocalDate inicioAnterior, LocalDate fimAnterior,
                               YearMonth primeiroMes, LocalDate inicioGeral) {

    public static PeriodoDashboard de(LocalDate de, LocalDate ate) {
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : fim.withDayOfMonth(1);
        if (!FaixaDataPermitida.contem(inicio) || !FaixaDataPermitida.contem(fim)) {
            throw BusinessException.explicado("Período inválido", FaixaDataPermitida.MENSAGEM,
                    "O painel só calcula períodos dentro desse intervalo.",
                    "Escolha datas entre os anos 2000 e 2100.");
        }
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
        return new PeriodoDashboard(inicio, fim, inicioAnterior, fimAnterior, primeiroMes, inicioGeral);
    }
}
