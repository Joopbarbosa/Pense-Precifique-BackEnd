package com.penseprecifique.api.shared.dto.request.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** #491 (V0.12.0) — taxa de uma parcela, quando a taxa varia por parcela. */
public record TaxaParcelaRequestDTO(
        @NotNull(message = "O número da parcela é obrigatório")
        @Min(value = 1, message = "Parcela inválida")
        @Max(value = 24, message = "Parcela inválida")
        Integer parcela,

        @NotNull(message = "A taxa da parcela é obrigatória")
        @DecimalMin(value = "0", message = "A taxa não pode ser negativa")
        BigDecimal taxa
) {}
