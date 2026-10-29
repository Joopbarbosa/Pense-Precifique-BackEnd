package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RN-NOVA-39 (V0.18.0, #1166) — quantidade a produzir informada para um produto com insumo não
 * fracionável ao criar a produção a partir do orçamento. Ausente = o menor múltiplo do rendimento
 * que cobre o pedido.
 */
@Getter
@Setter
public class QuantidadeProducaoRequest {

    @NotNull(message = "O produto é obrigatório")
    private UUID produtoId;

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    @Digits(integer = 9, fraction = 4, message = "A quantidade informada é inválida")
    private BigDecimal quantidade;
}
