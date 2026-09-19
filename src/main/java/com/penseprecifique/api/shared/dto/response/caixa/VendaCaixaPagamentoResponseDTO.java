package com.penseprecifique.api.shared.dto.response.caixa;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaPagamentoResponseDTO(
        UUID id,
        UUID metodoPagamentoId,
        BigDecimal valor,
        Integer parcelas,
        BigDecimal taxaPercentualAplicada
) {}
