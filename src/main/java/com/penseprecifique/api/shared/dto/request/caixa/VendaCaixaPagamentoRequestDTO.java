package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaPagamentoRequestDTO(
        @NotNull(message = "O método de pagamento é obrigatório")
        UUID metodoPagamentoId,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor,

        /** #487 (V0.12.0) — só aceito em Cartão de Crédito, limitado pela parcela máxima
         *  configurada no método. Nulo = à vista. */
        @Min(value = 1, message = "O número de parcelas deve ser pelo menos 1")
        Integer parcelas
) {}
