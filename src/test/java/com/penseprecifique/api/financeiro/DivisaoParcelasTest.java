package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.19.0 (#1172, RN-NOVA-5) — divisão em centavos inteiros e vencimentos mensais. Valores esperados vêm da regra, não do código. */
class DivisaoParcelasTest {

    @Test
    void cemEmTresFicaTrintaETresTrintaETresETrintaEQuatro() {
        List<NovaParcela> p = DivisaoParcelas.dividir(new BigDecimal("100.00"), 3, LocalDate.of(2030, 1, 10));
        assertEquals(new BigDecimal("33.33"), p.get(0).valor());
        assertEquals(new BigDecimal("33.33"), p.get(1).valor());
        assertEquals(new BigDecimal("33.34"), p.get(2).valor());
        assertEquals(new BigDecimal("100.00"), p.stream().map(NovaParcela::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void vencimentoMensalPartindoDoDiaOriginal() {
        List<NovaParcela> p = DivisaoParcelas.dividir(new BigDecimal("90.00"), 3, LocalDate.of(2030, 1, 31));
        assertEquals(LocalDate.of(2030, 1, 31), p.get(0).vencimento());
        assertEquals(LocalDate.of(2030, 2, 28), p.get(1).vencimento());
        assertEquals(LocalDate.of(2030, 3, 31), p.get(2).vencimento());
    }

    @Test
    void valorNaoRedondoEmSeteParcelas() {
        // 1234,56 -> 123456 centavos / 7 = 17636 resto 4 -> seis de 176,36 e a última de 176,40
        List<NovaParcela> p = DivisaoParcelas.dividir(new BigDecimal("1234.56"), 7, LocalDate.of(2030, 5, 5));
        assertEquals(new BigDecimal("176.36"), p.get(0).valor());
        assertEquals(new BigDecimal("176.40"), p.get(6).valor());
    }

    @Test
    void umaParcelaESessentaSaoAceitas() {
        assertEquals(1, DivisaoParcelas.dividir(new BigDecimal("10.00"), 1, LocalDate.of(2030, 1, 1)).size());
        assertEquals(60, DivisaoParcelas.dividir(new BigDecimal("600.00"), 60, LocalDate.of(2030, 1, 1)).size());
    }

    @Test
    void recusaQuantidadeValorEDataInvalidos() {
        LocalDate d = LocalDate.of(2030, 1, 1);
        BigDecimal cem = new BigDecimal("100.00");
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(cem, 0, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(cem, 61, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(cem, -3, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(null, 2, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(BigDecimal.ZERO, 2, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(new BigDecimal("-5.00"), 2, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(new BigDecimal("0.001"), 1, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(new BigDecimal("100000000.00"), 2, d));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(cem, 2, null));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(cem, 2, LocalDate.of(1999, 12, 31)));
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(cem, 2, LocalDate.of(2101, 1, 1)));
    }

    /** CEN-NOVO-58 — valor pequeno demais para o número de parcelas. */
    @Test
    void valorPequenoDemaisParaDividir() {
        assertThrows(BusinessException.class, () -> DivisaoParcelas.dividir(new BigDecimal("0.02"), 3, LocalDate.of(2030, 1, 1)));
        assertEquals(3, DivisaoParcelas.dividir(new BigDecimal("0.03"), 3, LocalDate.of(2030, 1, 1)).size());
    }
}
