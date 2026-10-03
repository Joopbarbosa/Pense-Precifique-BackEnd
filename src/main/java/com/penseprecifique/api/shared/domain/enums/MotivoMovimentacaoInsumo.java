package com.penseprecifique.api.shared.domain.enums;

public enum MotivoMovimentacaoInsumo {
    COMPRA,
    BAIXA_MANUAL,
    PERDA,
    AVARIA,
    USO_EXTRA,
    CORRECAO,
    OUTRO,
    PRODUCAO,
    ORCAMENTO,
    ESTORNO_PRODUCAO,
    // V0.13.0 (#516, RN-NOVA-1/9) — Insumo passou a poder ser componente direto de Item de
    // Catálogo; venda desse item pelo Caixa agora baixa/reverte o estoque do Insumo também.
    CAIXA,
    // V0.15.0 (#544, RN-NOVA-9) — SAIDA gerada pelo cancelamento de uma compra CONFIRMADA; a ENTRADA
    // original fica com estornada = true.
    ESTORNO_COMPRA
}
