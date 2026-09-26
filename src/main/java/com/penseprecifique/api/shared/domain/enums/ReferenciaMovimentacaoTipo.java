package com.penseprecifique.api.shared.domain.enums;

public enum ReferenciaMovimentacaoTipo {
    PRODUCAO,
    ORCAMENTO,
    // #490 (V0.12.0) — referencia_id aponta para VendaCaixa.id. Válido em movimentacoes_produto e,
    // desde V0.13.0 (#516, V54), também em movimentacoes_insumo (insumo componente de item de catálogo
    // vendido no Caixa).
    CAIXA,
    // V0.15.0 (#541, DT-NOVA-4) — referencia_id aponta para Compra.id. Só em movimentacoes_insumo.
    // Substitui LOTE_COMPRA, removido junto com os registros de compra antigos (V60/V62).
    COMPRA
}
