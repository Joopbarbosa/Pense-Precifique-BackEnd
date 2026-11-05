package com.penseprecifique.api.financeiro;

/** Filtro de situação das listas (RN-NOVA-4). TODAS = Em aberto + Pagas/Recebidas + Canceladas com selo (RN-NOVA-59, PP-707); os demais filtros nunca trazem canceladas. */
public enum FiltroSituacaoParcela { EM_ABERTO, VENCIDAS, BAIXADAS, TODAS }
