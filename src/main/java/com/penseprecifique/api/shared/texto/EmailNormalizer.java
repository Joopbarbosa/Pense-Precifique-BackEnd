package com.penseprecifique.api.shared.texto;

import java.util.Locale;

/**
 * #699 (RN-NOVA-PP699-1) — forma canônica do e-mail de acesso: sem espaços nas pontas e em minúsculas
 * ({@link Locale#ROOT}, independente do idioma do servidor). Cadastro, login e troca de e-mail usam esta
 * mesma forma, então "A@x.com" e "a@x.com" são a mesma conta.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {}

    /** Devolve o e-mail aparado e em minúsculas; {@code null} continua {@code null} (a validação recusa). */
    public static String normalizar(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
