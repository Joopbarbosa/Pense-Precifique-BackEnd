package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaItemRequestDTO(
        @NotNull(message = "O produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
        BigDecimal quantidade
) {}
