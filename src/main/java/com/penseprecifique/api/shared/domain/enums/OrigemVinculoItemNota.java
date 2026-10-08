package com.penseprecifique.api.shared.domain.enums;

/** V0.16.0 (#681, DT-NOVA-11) — como a ligação gravada no vínculo foi feita. */
public enum OrigemVinculoItemNota {
    CASAMENTO_NOME,
    SUGESTAO_IA,
    MANUAL,
    /** #718 (RN-NOVA-26) — ligação aceita a partir do vínculo de outro fornecedor com o mesmo nome de item. */
    OUTRO_FORNECEDOR
}
