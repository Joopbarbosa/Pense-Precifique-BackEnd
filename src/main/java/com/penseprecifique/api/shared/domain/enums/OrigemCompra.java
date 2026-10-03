package com.penseprecifique.api.shared.domain.enums;

/**
 * #541 (V0.15.0, DT-NOVA-3) — origem do registro da compra. Só MANUAL por enquanto; a coluna já
 * existe para NFC-e por QR (#561) e NF-e por PDF/XML (#562) entrarem sem refazer a compra.
 */
public enum OrigemCompra {
    MANUAL
}
