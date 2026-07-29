package com.penseprecifique.api.util;

/**
 * RN-053 — identificador exibido ao usuario (ex: INS-1, CTG-2), sem zero a esquerda.
 * Nunca usado em DTOs de PDF (dto/pdf) — RN-053 continua valendo lá (o prefixo, ex. "ORC-", nunca
 * aparece em PDF/recibo). Decisão de 2026-07-29 (Frente 6/P-BE-CONSOLIDADO-001): PdfMapper.numeroFormatado
 * deixou de fazer zero-padding artificial ("%04d") — expõe o número puro (orc.getNumero() como string,
 * ex. "47"), mas sem prefixo — não é o mesmo formato deste util, que sempre inclui o prefixo.
 */
public final class IdentificadorFormatter {

    private IdentificadorFormatter() {
    }

    public static String formatar(String prefixo, Integer numero) {
        return prefixo + "-" + numero;
    }
}
