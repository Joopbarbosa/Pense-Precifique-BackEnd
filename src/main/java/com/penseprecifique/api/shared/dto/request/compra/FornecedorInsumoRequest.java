package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** #540/RN-NOVA-6 (V0.15.0) — criar vínculo manual Fornecedor↔Insumo. */
public record FornecedorInsumoRequest(
        @NotNull(message = "Escolha o fornecedor")
        UUID fornecedorId,

        @NotNull(message = "Escolha o insumo")
        UUID insumoId,

        @Positive(message = "O preço de referência deve ser maior que zero")
        @Digits(integer = 11, fraction = 4, message = "Preço de referência com no máximo 4 casas decimais")
        BigDecimal precoReferencia
) {}
