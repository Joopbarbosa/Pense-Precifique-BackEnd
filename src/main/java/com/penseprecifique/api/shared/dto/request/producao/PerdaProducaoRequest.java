package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
    @Digits(integer = 11, fraction = 4, message = "Quantidade perdida com no máximo 11 dígitos inteiros e 4 casas decimais")
    private BigDecimal quantidadePerdida;
}
