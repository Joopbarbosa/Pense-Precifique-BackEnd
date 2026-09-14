package com.penseprecifique.api.shared.domain.enums;

public enum TipoOrigemProducao {
    DIVISAO, AGRUPAMENTO,
    /** RN-NOVA-5 (V0.10.0, #450) — inverso de AGRUPAMENTO: 1 produção filha nova por produto. */
    DESAGRUPAMENTO
}
