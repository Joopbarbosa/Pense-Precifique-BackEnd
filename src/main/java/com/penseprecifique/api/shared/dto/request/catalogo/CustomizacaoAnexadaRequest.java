package com.penseprecifique.api.shared.dto.request.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CustomizacaoAnexadaRequest {

    @NotNull(message = "O produto da customização é obrigatório")
    private UUID produtoId;

    @NotNull(message = "A quantidade é obrigatória")
    @DecimalMin(value = "0.0001", message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;
}
