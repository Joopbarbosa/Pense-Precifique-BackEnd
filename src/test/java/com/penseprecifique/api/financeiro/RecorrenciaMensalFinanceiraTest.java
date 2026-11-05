package com.penseprecifique.api.financeiro;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecorrenciaMensalFinanceiraTest {
    @Test
    void voltaAoDiaOriginalDepoisDeFevereiro() {
        LocalDate inicio = LocalDate.of(2027, 1, 31);
        assertEquals(LocalDate.of(2027, 1, 31), RecorrenciaMensalFinanceira.vencimento(inicio, 0));
        assertEquals(LocalDate.of(2027, 2, 28), RecorrenciaMensalFinanceira.vencimento(inicio, 1));
        assertEquals(LocalDate.of(2027, 3, 31), RecorrenciaMensalFinanceira.vencimento(inicio, 2));
        assertEquals(LocalDate.of(2027, 4, 30), RecorrenciaMensalFinanceira.vencimento(inicio, 3));
    }
}
