package com.penseprecifique.api.shared.dto.request.caixa;

import com.penseprecifique.api.shared.domain.enums.TipoCaixaMovimento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** RN-NOVA-8 — motivo mín. 30 caracteres, mesmo padrão de PDT-009 (BaixaManualProdutoRequest). */
public record CaixaMovimentoRequestDTO(
        @NotNull(message = "O tipo é obrigatório")
        TipoCaixaMovimento tipo,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor,

        @NotBlank(message = "O motivo é obrigatório")
        @Size(min = 30, message = "O motivo deve ter no mínimo 30 caracteres")
        String motivo
) {}
