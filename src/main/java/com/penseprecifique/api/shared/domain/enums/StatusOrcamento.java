package com.penseprecifique.api.shared.domain.enums;

// RN-NOVA-10 (V0.10.0, #466, altera ORC-005) — troca FINALIZADO->ENTREGUE->PAGO por
// FINALIZADO->PAGO->ENTREGUE; única mudança na sequência, resto do fluxo intacto.
public enum StatusOrcamento {
    RASCUNHO, ENVIADO, APROVADO, AGUARDANDO_SINAL, SINAL_PAGO,
    EM_PRODUCAO, FINALIZADO, PAGO, ENTREGUE, CANCELADO
}
