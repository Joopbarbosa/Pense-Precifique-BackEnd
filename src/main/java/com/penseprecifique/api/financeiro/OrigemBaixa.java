package com.penseprecifique.api.financeiro;

/**
 * V0.19.0 (DT-NOVA-1) — AUTOMATICA: a parcela nasceu baixada (ex.: compra paga por método que não é crédito);
 * USUARIA: baixa pela ação "Paguei"/"Recebi". Só USUARIA conta como baixada para bloquear alteração/cancelamento da compra (RN-NOVA-8).
 */
public enum OrigemBaixa { AUTOMATICA, USUARIA }
