package com.penseprecifique.api.shared.dto.request.insumo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemLoteCompraRequestDTO(

        @NotNull(message = "O id do insumo é obrigatório")
        UUID insumoId,

        @NotNull(message = "A quantidade comprada é obrigatória")
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
        BigDecimal quantidadeComprada,

        @NotNull(message = "O preço total pago é obrigatório")
        @DecimalMin(value = "0.01", message = "O preço total deve ser maior que zero")
        BigDecimal precoTotalPago
) {}
