package com.penseprecifique.api.financeiro;

import java.time.LocalDate;

/** Datas mensais calculadas sempre a partir do vencimento original, sem acumular o ajuste de fevereiro. */
final class RecorrenciaMensalFinanceira {
    private RecorrenciaMensalFinanceira() {}

    static LocalDate vencimento(LocalDate inicial, int indice) {
        LocalDate mes = inicial.withDayOfMonth(1).plusMonths(indice);
        return mes.withDayOfMonth(Math.min(inicial.getDayOfMonth(), mes.lengthOfMonth()));
    }
}
