package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #541 (V0.15.0) — linha da compra. {@code fornecedorId} só é lido no modo múltiplos fornecedores;
 * no modo único, a linha recebe o fornecedor do cabeçalho.
 */
public record CompraItemRequest(
        @NotNull(message = "Escolha o insumo")
        UUID insumoId,

        UUID fornecedorId,

        @Positive(message = "A quantidade deve ser maior que zero")
        @Digits(integer = 11, fraction = 4, message = "Quantidade com no máximo 4 casas decimais")
        BigDecimal quantidade,

        @Positive(message = "O preço total deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "Preço total com no máximo 2 casas decimais")
        BigDecimal precoTotal
) {}
