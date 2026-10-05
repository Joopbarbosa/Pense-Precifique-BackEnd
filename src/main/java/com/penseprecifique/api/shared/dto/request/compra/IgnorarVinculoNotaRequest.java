package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record IgnorarVinculoNotaRequest(@NotNull Boolean ignorar, UUID insumoId,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal fator) {}
