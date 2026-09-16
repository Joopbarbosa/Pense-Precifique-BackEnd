package com.penseprecifique.api.shared.dto.response.caixa;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaItemResponseDTO(
        UUID id,
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {}
