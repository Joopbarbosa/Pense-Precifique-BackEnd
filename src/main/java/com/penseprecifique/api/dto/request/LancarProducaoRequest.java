package com.penseprecifique.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class LancarProducaoRequest {

    @NotNull(message = "O produto é obrigatório")
    private UUID produtoId;

    // RN-051 — informa quantidade (fluxo livre, todos insumos fracionáveis) OU lotes (algum insumo
    // não-fracionável); "exatamente um dos dois" ainda não é validado aqui, refinado no P-026.
    @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @Min(value = 1, message = "O número de lotes deve ser pelo menos 1")
    private Integer lotes;

    private LocalDateTime dataProducao;
}
