package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** #550/RN-NOVA-23 + S2 (V0.15.0) — PATCH /compras/{id}/pagamento em compra CONFIRMADA. */
public record PagamentoCompraRequest(
        @NotNull(message = "Informe se a compra foi paga")
        Boolean pago,

        UUID metodoPagamentoId
) {}
