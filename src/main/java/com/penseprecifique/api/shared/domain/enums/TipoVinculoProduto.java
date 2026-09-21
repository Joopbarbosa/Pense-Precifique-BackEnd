package com.penseprecifique.api.shared.domain.enums;

public enum TipoVinculoProduto {
    /** V0.13.0 (DT-NOVA-1) — unifica os antigos ITEM_CATALOGO_PRINCIPAL e CUSTOMIZACAO_ANEXADA:
     * desde RN-NOVA-1, um Item de Catálogo tem N componentes genéricos (sem distinção entre
     * "produto principal" e "customização anexada"), então o vínculo também deixa de ter 2 tipos. */
    ITEM_CATALOGO_COMPONENTE, COMPONENTE_FICHA_TECNICA
}
