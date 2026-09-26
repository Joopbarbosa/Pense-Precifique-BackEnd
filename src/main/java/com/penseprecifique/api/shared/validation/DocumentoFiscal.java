package com.penseprecifique.api.shared.validation;

import java.util.Locale;

/**
 * #536/RN-NOVA-17 (V0.15.0, DT-NOVA-1) — normalização e validação de CPF e CNPJ, em {@code shared/}
 * porque Fornecedor e, no futuro, a leitura de NFC-e/NF-e (#561/#562) usam a mesma regra.
 *
 * <p>CNPJ alfanumérico (IN RFB 2.229/2024, vigente desde jul/2026): as 12 primeiras posições
 * aceitam A–Z e 0–9, as 2 últimas (DV) só dígitos. O DV é módulo 11 com o valor de cada caractere
 * igual ao código ASCII − 48 — para dígitos dá o próprio dígito, então o CNPJ numérico antigo
 * continua válido pela mesma conta.
 */
public final class DocumentoFiscal {

    private static final int[] PESOS_CNPJ_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private DocumentoFiscal() {
    }

    /** Remove máscara (pontuação e espaços) e converte para maiúsculo. */
    public static String normalizar(String documento) {
        if (documento == null) {
            return null;
        }
        String limpo = documento.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        return limpo.isEmpty() ? null : limpo;
    }

    /** Espera o CPF já normalizado (11 dígitos). */
    public static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = dvCpf(cpf, 9);
        int dv2 = dvCpf(cpf, 10);
        return cpf.charAt(9) - '0' == dv1 && cpf.charAt(10) - '0' == dv2;
    }

    /** Espera o CNPJ já normalizado (14 caracteres, maiúsculo). */
    public static boolean cnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}\\d{2}") || cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = dvCnpj(cnpj.substring(0, 12), PESOS_CNPJ_DV1);
        int dv2 = dvCnpj(cnpj.substring(0, 12) + dv1, PESOS_CNPJ_DV2);
        return cnpj.charAt(12) - '0' == dv1 && cnpj.charAt(13) - '0' == dv2;
    }

    private static int dvCpf(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        }
        return (soma * 10) % 11 % 10;
    }

    private static int dvCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (base.charAt(i) - 48) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
