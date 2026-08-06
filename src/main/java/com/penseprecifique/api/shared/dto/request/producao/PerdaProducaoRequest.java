package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class PerdaProducaoRequest {

    @NotNull(message = "O produto é obrigatório")
    private UUID produtoId;

    @NotNull(message = "A quantidade perdida é obrigatória")
    @DecimalMin(value = "0", message = "A quantidade perdida não pode ser negativa")
    private BigDecimal quantidadePerdida;
}
