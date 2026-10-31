package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** PP-662 (DT-NOVA-17) — atraso da compra pendente calculado pelo relógio injetado (RN-NOVA-6 da V0.18.0). */
class CompraMapperRelogioTest {

    private final CompraMapper mapper = new CompraMapper(
            Clock.fixed(Instant.parse("2026-06-15T15:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    private Compra pendente(LocalDate previsao) {
        return Compra.builder().status(StatusCompra.PENDENTE_RECEBIMENTO).previsaoEntrega(previsao).build();
    }

    @Test
    void previsaoPassadaContaDiasInteirosAteHojeDoRelogio() {
        assertEquals(3, mapper.diasAtraso(pendente(LocalDate.of(2026, 6, 12))));
    }

    @Test
    void previsaoIgualAHojeOuFuturaNaoEhAtraso() {
        assertNull(mapper.diasAtraso(pendente(LocalDate.of(2026, 6, 15))));
        assertNull(mapper.diasAtraso(pendente(LocalDate.of(2026, 6, 16))));
    }

    @Test
    void semPrevisaoOuForaDePendenteNaoHaAtraso() {
        assertNull(mapper.diasAtraso(pendente(null)));
        assertNull(mapper.diasAtraso(Compra.builder().status(StatusCompra.CONFIRMADA)
                .previsaoEntrega(LocalDate.of(2020, 1, 1)).build()));
    }
}
