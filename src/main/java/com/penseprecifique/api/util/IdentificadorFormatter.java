package com.penseprecifique.api.util;

/**
 * RN-053 — identificador exibido ao usuario (ex: INS-1, CTG-2), sem zero a esquerda.
 * Nunca usado em DTOs de PDF (dto/pdf) — la o numero segue o padrao proprio do PdfMapper (ex: "%04d").
 */
public final class IdentificadorFormatter {

    private IdentificadorFormatter() {
    }

    public static String formatar(String prefixo, Integer numero) {
        return prefixo + "-" + numero;
    }
}
