package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BaixaManualInsumoRequestDTO(

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
        BigDecimal quantidade,

        @NotNull(message = "O motivo é obrigatório")
        MotivoMovimentacaoInsumo motivo,

        @NotBlank(message = "A observação é obrigatória")
        @Size(min = 30, message = "A observação deve ter no mínimo 30 caracteres")
        String observacao
) {}
