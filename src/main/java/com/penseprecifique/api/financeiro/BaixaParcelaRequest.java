package com.penseprecifique.api.financeiro;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** V0.19.0 (RN-NOVA-3) — POST /financeiro/parcelas/{id}/baixa. Data omitida = hoje. */
public record BaixaParcelaRequest(
        LocalDate dataBaixa,
        @NotNull(message = "Informe a forma de pagamento.")
        UUID metodoPagamentoId
) {
}
