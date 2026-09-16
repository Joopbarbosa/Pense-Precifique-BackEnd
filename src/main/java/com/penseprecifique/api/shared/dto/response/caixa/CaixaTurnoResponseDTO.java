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
        StatusCaixaTurno status
) {}
