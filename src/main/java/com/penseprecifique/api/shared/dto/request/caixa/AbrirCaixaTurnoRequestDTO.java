package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AbrirCaixaTurnoRequestDTO(
        @NotNull(message = "O valor do fundo de troco é obrigatório")
        @DecimalMin(value = "0", message = "O valor do fundo de troco não pode ser negativo")
        BigDecimal valorAbertura
) {}
