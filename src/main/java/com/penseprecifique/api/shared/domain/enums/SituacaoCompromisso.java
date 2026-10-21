package com.penseprecifique.api.shared.domain.enums;

/**
 * V0.18.0 (RN-NOVA-5) — situação do compromisso. Só o compromisso da usuária chega concluído ou cancelado
 * (continua no dia, riscado); os de origem automática saem do calendário ao serem resolvidos.
 */
public enum SituacaoCompromisso {
    PENDENTE, CONCLUIDO, CANCELADO
}
