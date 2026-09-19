package com.penseprecifique.api.shared.dto.response.caixa;

import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaixaTurnoResponseDTO(
        UUID id,
        LocalDateTime dataAbertura,
        BigDecimal valorAbertura,
        LocalDateTime dataFechamento,
        BigDecimal valorFechamentoEsperado,
        BigDecimal valorFechamentoInformado,
        BigDecimal diferenca,
        /** #488 (V0.12.0) — preenchida só quando o fechamento teve diferença. */
        String fechamentoJustificativa,
        StatusCaixaTurno status
) {}
