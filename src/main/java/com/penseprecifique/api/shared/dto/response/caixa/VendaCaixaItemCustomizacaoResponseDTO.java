package com.penseprecifique.api.shared.dto.response.caixa;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaItemCustomizacaoResponseDTO(
        UUID id,
        UUID produtoId,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {}
