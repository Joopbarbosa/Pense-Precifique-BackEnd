package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** V0.18.0 (#549, RN-NOVA-18) — PATCH /compras/{id}/previsao: nova previsão da compra pendente de recebimento (hoje ou futura, COM-043). */
public record AdiarPrevisaoCompraRequest(
        @NotNull(message = "Informe a nova previsão de entrega")
        LocalDate previsaoEntrega
) {}
