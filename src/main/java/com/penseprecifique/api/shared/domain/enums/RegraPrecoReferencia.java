package com.penseprecifique.api.shared.domain.enums;

/**
 * V0.15.0 (#590, RN-NOVA-39) — como o preço de referência dos vínculos fornecedor↔insumo é
 * calculado. MEDIA e MENOR_VALOR olham as compras CONFIRMADAS do par nos últimos 12 meses; MANUAL
 * é o valor digitado (compras não mudam).
 */
public enum RegraPrecoReferencia {
    MEDIA, MENOR_VALOR, MANUAL
}
