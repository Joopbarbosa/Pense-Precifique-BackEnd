package com.penseprecifique.api.shared.dto.response.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConfiguracaoResponseDTO(
        UUID id,
        BigDecimal valorHora,
        BigDecimal margemPadrao,
        LocalDateTime updatedAt
) {}
