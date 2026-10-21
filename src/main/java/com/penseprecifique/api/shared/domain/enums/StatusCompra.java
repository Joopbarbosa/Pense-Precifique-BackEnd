package com.penseprecifique.api.shared.domain.enums;

/**
 * #541/RN-NOVA-4 (V0.15.0) — RASCUNHO → CONFIRMADA → CANCELADA (final). #958/RN-NOVA-5 (V0.17.0) —
 * RASCUNHO → PENDENTE_RECEBIMENTO (entrega não imediata) → CONFIRMADA (entrada) ou CANCELADA.
 */
public enum StatusCompra {
    RASCUNHO, PENDENTE_RECEBIMENTO, CONFIRMADA, CANCELADA
}
