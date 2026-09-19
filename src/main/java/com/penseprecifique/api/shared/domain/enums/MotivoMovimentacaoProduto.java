package com.penseprecifique.api.shared.domain.enums;

public enum MotivoMovimentacaoProduto {
    // CAIXA — #490 (V0.12.0): baixa (SAIDA) na venda de Caixa e reversão (ENTRADA) no
    // cancelamento, ambas com o mesmo motivo (RN-NOVA-14) — distinguidas pelo campo `tipo`.
    PRODUCAO, ORCAMENTO, PERDA, AVARIA, USO_EXTRA, CORRECAO, OUTRO, ESTORNO_PRODUCAO, CAIXA
}
