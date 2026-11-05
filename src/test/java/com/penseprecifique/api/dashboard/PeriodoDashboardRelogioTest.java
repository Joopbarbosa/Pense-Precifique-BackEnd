package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PP-662 (DT-NOVA-17, RN-NOVA-3) — o período padrão do dashboard vem do relógio injetado, no fuso de Brasília. */
class PeriodoDashboardRelogioTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private static Clock relogio(String instante) {
        return Clock.fixed(Instant.parse(instante), BRASILIA);
    }

    @Test
    void semDatasUsaOMesAtualDoRelogioInjetado() {
        PeriodoDashboard p = PeriodoDashboard.de(null, null, relogio("2026-06-15T15:00:00Z"));
        assertEquals(LocalDate.of(2026, 6, 1), p.inicio());
        assertEquals(LocalDate.of(2026, 6, 15), p.fim());
    }

    @Test
    void viradaDeMesEmUtcAindaEhODiaDeBrasilia() {
        // 01/07 02:30 UTC = 30/06 23:30 em Brasília: continua junho.
        PeriodoDashboard p = PeriodoDashboard.de(null, null, relogio("2026-07-01T02:30:00Z"));
        assertEquals(LocalDate.of(2026, 6, 30), p.fim());
        assertEquals(LocalDate.of(2026, 6, 1), p.inicio());
    }

    @Test
    void datasInformadasPrevalecemSobreORelogio() {
        PeriodoDashboard p = PeriodoDashboard.de(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), relogio("2030-01-01T12:00:00Z"));
        assertEquals(LocalDate.of(2026, 1, 31), p.fim());
    }

    @Test
    void entradaHostilContinuaRecusadaComRelogioInjetado() {
        Clock c = relogio("2026-06-15T15:00:00Z");
        assertThrows(BusinessException.class, () -> PeriodoDashboard.de(LocalDate.of(1999, 12, 31), null, c));
        assertThrows(BusinessException.class, () -> PeriodoDashboard.de(null, LocalDate.of(2101, 1, 1), c));
        assertThrows(BusinessException.class, () -> PeriodoDashboard.de(LocalDate.of(9999, 12, 31), LocalDate.of(9999, 12, 31), c));
        assertThrows(BusinessException.class, () -> PeriodoDashboard.de(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1), c));
    }
}
