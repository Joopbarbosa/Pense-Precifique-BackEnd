package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** #540 (V0.15.0) — editar o preço de referência de um vínculo; nulo limpa o preço. */
public record PrecoReferenciaRequest(
        @Positive(message = "O preço de referência deve ser maior que zero")
        @Digits(integer = 11, fraction = 4, message = "Preço de referência com no máximo 4 casas decimais")
        BigDecimal precoReferencia
) {}
