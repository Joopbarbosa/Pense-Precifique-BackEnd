package com.penseprecifique.api.shared.domain.enums;

/**
 * #541 (V0.15.0, DT-NOVA-3) — origem do registro da compra. V0.16.0 (#683, DT-NOVA-10): NFC-e por QR
 * (#561) e NF-e por PDF, foto ou XML (#562) entram sem refazer a compra.
 */
public enum OrigemCompra {
    MANUAL,
    NFCE_QR,
    NFE_PDF,
    NFE_FOTO,
    NFE_XML
}
