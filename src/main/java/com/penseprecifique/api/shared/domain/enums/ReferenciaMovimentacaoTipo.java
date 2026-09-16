package com.penseprecifique.api.shared.domain.enums;

public enum ReferenciaMovimentacaoTipo {
    PRODUCAO,
    ORCAMENTO,
    LOTE_COMPRA,
    // #490 (V0.12.0) — só válido em movimentacoes_produto (ver CHECK da tabela); referencia_id
    // aponta para VendaCaixa.id quando referencia_tipo = CAIXA (RN-NOVA-14).
    CAIXA
}
