package com.penseprecifique.api.whatsapp;

/** RN-NOVA-27 — normalização do número para a forma canônica (só dígitos, BR com DDD ganha 55). */
public final class NumeroWhatsapp {

    private NumeroWhatsapp() { }

    /** @return forma canônica ou {@code null} se o número for inválido. */
    public static String canonico(String entrada) {
        if (entrada == null || entrada.length() > 40) return null;
        String s = entrada.strip();
        boolean internacional = s.startsWith("+");
        if (internacional) s = s.substring(1);
        StringBuilder digitos = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') digitos.append(c);
            else if (c == ' ' || c == '(' || c == ')' || c == '-') continue;
            else return null;
        }
        int n = digitos.length();
        if (internacional) return n >= 8 && n <= 15 ? digitos.toString() : null;
        if (n == 10 || n == 11) return "55" + digitos;
        return n >= 12 && n <= 15 ? digitos.toString() : null;
    }
}
