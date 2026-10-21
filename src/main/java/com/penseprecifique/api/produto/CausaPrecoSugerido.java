package com.penseprecifique.api.produto;

/** #672/RN-NOVA-10 (V0.17.0) — o que provocou a mudança do preço sugerido de um produto. */
public enum CausaPrecoSugerido {
    COMPRA_ENTRADA, COMPRA_CANCELAMENTO, PRODUTO_EDICAO, PRODUTO_MARGEM, INSUMO_SUBSTITUICAO, VALOR_HORA,
    /** V0.18.0 (#961) — edição do próprio item de Catálogo (componentes, tempo) e mudança só da margem do item. */
    ITEM_EDICAO, ITEM_MARGEM
}
