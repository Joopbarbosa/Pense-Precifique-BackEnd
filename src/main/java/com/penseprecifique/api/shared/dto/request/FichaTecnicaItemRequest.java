package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FichaTecnicaItemRequest {

    private UUID insumoId;

    private UUID produtoBaseId;

    @NotNull(message = "A quantidade é obrigatória")
    @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;
}
