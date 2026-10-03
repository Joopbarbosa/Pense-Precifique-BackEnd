package com.penseprecifique.api.insumo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INS-004 + RN-084 (blindagem). Casos portados de LoteCompraServiceMediaPonderadaIT (removido com o
 * fluxo de lote na V0.15.0) mais os do CEN-NOVO-7.
 */
class CustoMedioPonderadoTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void casoTrivialEstoquePositivo() {
        // 10 un. a R$ 10,00 + 10 un. por R$ 200,00 → (100 + 200) / 20 = 15,00
        assertEquals(bd("15.0000"), CustoMedioPonderado.calcular(bd("10"), bd("10"), bd("10"), bd("200")));
    }

    @Test
    void cen7_estoqueZeroUsaSoACompra() {
        // Papel Couché: estoque 0, custo 0,40; 500 un. por R$ 250,00 → 0,50
        assertEquals(bd("0.5000"), CustoMedioPonderado.calcular(BigDecimal.ZERO, bd("0.40"), bd("500"), bd("250.00")));
        // Cola Branca: estoque 0, custo 12,00; 3 un. por R$ 45,00 → 15,00
        assertEquals(bd("15.0000"), CustoMedioPonderado.calcular(BigDecimal.ZERO, bd("12.00"), bd("3"), bd("45.00")));
    }

    @Test
    void estoqueNegativoNaoCompensadoNaoGeraCustoNegativo() {
        // estoque −20 tratado como 0: 25 un. por R$ 125,00 → 5,00
        assertEquals(bd("5.0000"), CustoMedioPonderado.calcular(bd("-20"), bd("8"), bd("25"), bd("125")));
    }

    @Test
    void compraQueCompensaExatamenteODeficitNaoDivideporZero() {
        assertEquals(bd("10.0000"), CustoMedioPonderado.calcular(bd("-10"), bd("8"), bd("10"), bd("100")));
    }

    @Test
    void valorNaoRedondoArredondaPara4Casas() {
        // 3 un. a R$ 12,00 + 7 un. por R$ 91,37 → (36 + 91,37) / 10 = 12,737
        assertEquals(bd("12.7370"), CustoMedioPonderado.calcular(bd("3"), bd("12.00"), bd("7"), bd("91.37")));
        // 1 un. a R$ 1,00 + 2 un. por R$ 1,00 → 2 / 3 = 0,66666… → 0,6667
        assertEquals(bd("0.6667"), CustoMedioPonderado.calcular(bd("1"), bd("1"), bd("2"), bd("1")));
    }
}
