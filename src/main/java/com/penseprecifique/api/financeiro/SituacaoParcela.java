package com.penseprecifique.api.financeiro;

/**
 * V0.19.0 (RN-NOVA-2) — situação gravada da parcela. BAIXADA é "Paga" no a pagar e "Recebida" no a receber (o rótulo
 * sai do tipo do lançamento). "Vencida" e "Vence hoje" nunca são gravadas (DT-NOVA-2).
 */
public enum SituacaoParcela { EM_ABERTO, BAIXADA, CANCELADA }
