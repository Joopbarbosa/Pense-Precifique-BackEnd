package com.penseprecifique.api.financeiro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EditarOcorrenciaRequest(@Size(min = 2, max = 80) String descricao,
        @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal valor,
        LocalDate vencimento, UUID categoriaId, Boolean foraProjecao, Boolean atualizarCategoria) {
    public EditarOcorrenciaRequest(String descricao, BigDecimal valor, LocalDate vencimento, UUID categoriaId, Boolean foraProjecao) {
        this(descricao, valor, vencimento, categoriaId, foraProjecao, false);
    }
}
