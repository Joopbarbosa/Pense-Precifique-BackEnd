package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaPagamentoRequestDTO(
        @NotNull(message = "O método de pagamento é obrigatório")
        UUID metodoPagamentoId,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor
) {}
