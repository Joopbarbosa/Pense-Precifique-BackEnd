package com.penseprecifique.api.shared.dto.response.caixa;

import com.penseprecifique.api.shared.domain.enums.TipoCaixaMovimento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaixaMovimentoResponseDTO(
        UUID id,
        TipoCaixaMovimento tipo,
        BigDecimal valor,
        String motivo,
        LocalDateTime dataMovimento,
        UUID responsavelId
) {}
