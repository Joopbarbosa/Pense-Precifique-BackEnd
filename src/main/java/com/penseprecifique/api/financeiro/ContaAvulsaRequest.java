package com.penseprecifique.api.financeiro;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContaAvulsaRequest(
        @NotNull TipoLancamento tipo,
        @NotBlank @Size(min = 2, max = 80) String descricao,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal valor,
        @NotNull LocalDate vencimento,
        UUID categoriaId,
        @Min(2) @Max(24) Integer meses,
        Boolean foraProjecao,
        Boolean jaPaga,
        LocalDate dataBaixa,
        UUID metodoPagamentoId) {}
