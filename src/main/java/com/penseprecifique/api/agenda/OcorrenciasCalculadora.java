package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.domain.entity.CompromissoUsuaria;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * V0.18.0 (#1039, DT-NOVA-5, RN-NOVA-20) — gera, por cálculo, as datas originais das ocorrências de um compromisso da
 * usuária. Nada é gravado. A série começa na data de início (inclusive) e termina após N ocorrências (2 a 365) ou é
 * indefinida. Mensal nos dias 29 a 31 cai no último dia dos meses mais curtos.
 */
@Component
public class OcorrenciasCalculadora {

    /** Datas originais das ocorrências que caem entre {@code de} e {@code ate} (inclusive), em ordem crescente. */
    public List<LocalDate> datas(CompromissoUsuaria c, LocalDate de, LocalDate ate) {
        List<LocalDate> resultado = new ArrayList<>();
        if (ate.isBefore(c.getDataInicio()) || ate.isBefore(de)) {
            return resultado;
        }
        switch (c.getRecorrencia()) {
            case NENHUMA -> {
                if (!c.getDataInicio().isBefore(de)) {
                    resultado.add(c.getDataInicio());
                }
            }
            case SEMANAL -> semanal(c, de, ate, resultado);
            case MENSAL -> mensal(c, de, ate, resultado);
        }
        return resultado;
    }

    /** true se a data é uma das ocorrências da série (a data original, sem considerar mudanças de dia). */
    public boolean ehOcorrencia(CompromissoUsuaria c, LocalDate data) {
        return datas(c, data, data).contains(data);
    }

    public static Set<DayOfWeek> diasDaSemana(CompromissoUsuaria c) {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (c.getDiasSemana() != null && !c.getDiasSemana().isBlank()) {
            for (String d : c.getDiasSemana().split(",")) {
                dias.add(DayOfWeek.valueOf(d.trim()));
            }
        }
        return dias;
    }

    private void semanal(CompromissoUsuaria c, LocalDate de, LocalDate ate, List<LocalDate> destino) {
        Set<DayOfWeek> dias = diasDaSemana(c);
        if (dias.isEmpty()) {
            return;
        }
        Integer limite = c.getQuantidadeOcorrencias();
        int contadas = 0;
        // a contagem das N ocorrências começa na data de início, então percorre desde ali (no máximo 365 ocorrências)
        for (LocalDate dia = c.getDataInicio(); !dia.isAfter(ate); dia = dia.plusDays(1)) {
            if (!dias.contains(dia.getDayOfWeek())) {
                continue;
            }
            if (limite != null && contadas >= limite) {
                return;
            }
            contadas++;
            if (!dia.isBefore(de)) {
                destino.add(dia);
            }
        }
    }

    private void mensal(CompromissoUsuaria c, LocalDate de, LocalDate ate, List<LocalDate> destino) {
        int diaDoMes = c.getDataInicio().getDayOfMonth();
        YearMonth inicio = YearMonth.from(c.getDataInicio());
        Integer limite = c.getQuantidadeOcorrencias();
        for (int k = 0; limite == null || k < limite; k++) {
            YearMonth mes = inicio.plusMonths(k);
            LocalDate data = mes.atDay(Math.min(diaDoMes, mes.lengthOfMonth()));
            if (data.isAfter(ate)) {
                return;
            }
            if (!data.isBefore(de)) {
                destino.add(data);
            }
        }
    }
}
