package com.penseprecifique.api.shared.domain.enums;

/** #487 — RN-NOVA-10. Sem máquina de status como Orcamento (ORC-005): só o binário
 * concluída/cancelada — a venda de Caixa nasce sempre já concluída, não existe rascunho. */
public enum StatusVendaCaixa {
    CONCLUIDA, CANCELADA
}
