package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.mapper.OrcamentoMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RN-NOVA-11 (V0.18.0) — "Vencido" só em RASCUNHO/ENVIADO, a partir do dia seguinte à validade (hoje = 12/10/2026). */
class OrcamentoMapperVencidoTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), BRASILIA);
    private final OrcamentoMapper mapper = new OrcamentoMapper(new EntregaEstimadaCalculadora(), clock);

    private Orcamento orcamento(StatusOrcamento status, LocalDateTime validade) {
        return Orcamento.builder().cliente(Cliente.builder().nome("Maria").build())
                .status(status).dataValidade(validade).build();
    }

    @Test
    void enviadoComValidadePassadaEstaVencido() {
        assertTrue(mapper.toResponse(orcamento(StatusOrcamento.ENVIADO, LocalDateTime.of(2026, 10, 11, 23, 0))).isVencido());
        assertTrue(mapper.toResponse(orcamento(StatusOrcamento.RASCUNHO, LocalDateTime.of(2026, 10, 1, 0, 0))).isVencido());
    }

    @Test
    void naoVenceNoProprioDiaDaValidade() {
        assertFalse(mapper.toResponse(orcamento(StatusOrcamento.ENVIADO, LocalDateTime.of(2026, 10, 12, 9, 0))).isVencido());
        assertFalse(mapper.toResponse(orcamento(StatusOrcamento.ENVIADO, LocalDateTime.of(2026, 10, 13, 9, 0))).isVencido());
    }

    @Test
    void deAprovadoEmDianteECanceladoNuncaVence() {
        LocalDateTime passada = LocalDateTime.of(2026, 9, 1, 0, 0);
        for (StatusOrcamento s : new StatusOrcamento[] {StatusOrcamento.APROVADO, StatusOrcamento.AGUARDANDO_SINAL,
                StatusOrcamento.SINAL_PAGO, StatusOrcamento.EM_PRODUCAO, StatusOrcamento.FINALIZADO,
                StatusOrcamento.PAGO, StatusOrcamento.ENTREGUE, StatusOrcamento.CANCELADO}) {
            assertFalse(mapper.toResponse(orcamento(s, passada)).isVencido(), s.name());
        }
    }

    @Test
    void semDataDeValidadeNaoVence() {
        assertFalse(mapper.toResponse(orcamento(StatusOrcamento.ENVIADO, null)).isVencido());
    }
}
