package com.penseprecifique.api.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 #964 (RN-NOVA-19, CEN-NOVO-56 a 59): regra única de telefone. */
class ValidadorTelefoneTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "(11) 91234-5678", "(11) 1234-5678", "11 91234-5678", "11912345678", "+55 11 91234-5678", "+351 21 123 4567"})
    void aceitaVazioNacionalESetoEstrangeiroValidos(String telefone) {
        assertTrue(ValidadorTelefone.valido(telefone));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(11) 1234",          // incompleto
            "912345678",          // nacional com 9 dígitos
            "abc",                // só letras: BLOQUEIO, nunca vazio
            "(11) abcd-efgh",     // letras no meio
            "+55 11",             // estrangeiro com menos de 8 dígitos
            "+1234567",           // 7 dígitos
            "+1234567890123456",  // 16 dígitos
            "+55 11 91234-5678 9999999999", // acima de 25 caracteres
            "11912345678\u0000",  // caractere nulo
            "+\u000012345678"     // caractere nulo no estrangeiro
    })
    void recusaFormatosForaDaRegra(String telefone) {
        assertFalse(ValidadorTelefone.valido(telefone));
    }

    @Test
    void estrangeiroNoLimiteDe25CaracteresEValidoEUmAMaisEInvalido() {
        String noLimite = "+1-2-3-4-5-6-7-8-9-012345"; // 15 dígitos e 9 separadores
        assertEquals(25, noLimite.length());
        assertTrue(ValidadorTelefone.valido(noLimite));
        assertFalse(ValidadorTelefone.valido(noLimite + "0"));
    }
}
