package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** #550/RN-NOVA-23 + S2 (V0.15.0) — PATCH /compras/{id}/pagamento em compra CONFIRMADA. */
public record PagamentoCompraRequest(
        @NotNull(message = "Informe se a compra foi paga")
        Boolean pago,

        UUID metodoPagamentoId,

        /** #597/RN-NOVA-42 — só com método Cartão de crédito; ignorado nos demais. */
        Integer parcelas,

        /** V0.19.0 (#1173, RN-NOVA-7) — vencimento da parcela (Não paga) ou da 1ª parcela do cartão. */
        LocalDate vencimento
) {
    /** Forma anterior ao V0.19.0 (sem vencimento). */
    public PagamentoCompraRequest(Boolean pago, UUID metodoPagamentoId, Integer parcelas) {
        this(pago, metodoPagamentoId, parcelas, null);
    }

    /** Forma anterior ao #597. */
    public PagamentoCompraRequest(Boolean pago, UUID metodoPagamentoId) {
        this(pago, metodoPagamentoId, null, null);
    }
}
