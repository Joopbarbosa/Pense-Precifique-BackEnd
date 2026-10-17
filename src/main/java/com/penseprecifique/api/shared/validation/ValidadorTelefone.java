package com.penseprecifique.api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * V0.17.0 #964 (RN-NOVA-19, DT-NOVA-15) — regra única de telefone. Aceita só os dois formatos da
 * tela: nacional (10 ou 11 dígitos, com DDD) e estrangeiro ({@code +} seguido de 8 a 15 dígitos).
 * Separadores permitidos: espaço, parênteses e hífen. Texto sem dígito (ex.: "abc") é inválido,
 * nunca tratado como vazio. Caractere nulo cai na regra de formato e vira BLOQUEIO, nunca erro interno.
 */
public class ValidadorTelefone implements ConstraintValidator<TelefoneValido, String> {

    public static final String MENSAGEM = "Telefone inválido";
    public static final int TAMANHO_MAXIMO = 25;

    private static final String CARACTERES_NUMERO = "[0-9 ()\\-]+";
    private static final int NACIONAL_MIN = 10;
    private static final int NACIONAL_MAX = 11;
    private static final int ESTRANGEIRO_MIN = 8;
    private static final int ESTRANGEIRO_MAX = 15;

    /** Vazio é permitido; o campo obrigatório, quando houver, é validado à parte. */
    @Override
    public boolean isValid(String telefone, ConstraintValidatorContext context) {
        return valido(telefone);
    }

    public static boolean valido(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return true;
        }
        String texto = telefone.strip();
        if (texto.length() > TAMANHO_MAXIMO) {
            return false;
        }
        if (texto.startsWith("+")) {
            String numero = texto.substring(1);
            return numero.matches(CARACTERES_NUMERO) && contarDigitos(numero) >= ESTRANGEIRO_MIN
                    && contarDigitos(numero) <= ESTRANGEIRO_MAX;
        }
        if (!texto.matches(CARACTERES_NUMERO)) {
            return false;
        }
        int digitos = contarDigitos(texto);
        return digitos >= NACIONAL_MIN && digitos <= NACIONAL_MAX;
    }

    private static int contarDigitos(String texto) {
        return (int) texto.chars().filter(Character::isDigit).count();
    }
}
