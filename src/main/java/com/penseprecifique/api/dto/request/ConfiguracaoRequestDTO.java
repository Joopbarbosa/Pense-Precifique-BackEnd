package com.penseprecifique.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConfiguracaoRequestDTO(
        @NotNull(message = "O valor da hora é obrigatório")
        @DecimalMin(value = "0", message = "O valor da hora não pode ser negativo")
        BigDecimal valorHora,

        @NotNull(message = "A margem padrão é obrigatória")
        @DecimalMin(value = "0", message = "A margem não pode ser negativa")
        BigDecimal margemPadrao
) {}
