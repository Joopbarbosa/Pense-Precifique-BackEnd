package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ConsumoRealRequest {

    // XOR — mesmo padrão de ProducaoInsumoConsumido (insumo ou produto-base, nunca os dois).
    private UUID insumoId;
    private UUID produtoBaseId;

    @NotNull(message = "A quantidade consumida é obrigatória")
    @PositiveOrZero(message = "A quantidade consumida não pode ser negativa")
    private BigDecimal quantidadeConsumida;
}
