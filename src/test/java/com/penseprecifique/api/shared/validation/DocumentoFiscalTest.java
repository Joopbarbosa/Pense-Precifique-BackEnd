package com.penseprecifique.api.shared.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #536/RN-NOVA-17 — exemplos oficiais: CNPJ alfanumérico 12.ABC.345/01DE-35 (Receita Federal,
 * IN RFB 2.229/2024), CNPJ numérico 11.222.333/0001-81 e CPF 529.982.247-25.
 */
class DocumentoFiscalTest {

    @Test
    void normalizaMascaraEMinuscula() {
        assertEquals("12ABC34501DE35", DocumentoFiscal.normalizar("12.abc.345/01de-35"));
        assertEquals("52998224725", DocumentoFiscal.normalizar(" 529.982.247-25 "));
        assertNull(DocumentoFiscal.normalizar(" .-/ "));
    }

    @Test
    void cnpjAlfanumericoOficialValido() {
        assertTrue(DocumentoFiscal.cnpjValido("12ABC34501DE35"));
    }

    @Test
    void cnpjAlfanumericoComDvErradoInvalido() {
        assertFalse(DocumentoFiscal.cnpjValido("12ABC34501DE36"));
    }

    @Test
    void cnpjNumericoAntigoContinuaValido() {
        assertTrue(DocumentoFiscal.cnpjValido("11222333000181"));
        assertFalse(DocumentoFiscal.cnpjValido("11222333000182"));
    }

    @Test
    void cnpjComLetraNoDvOuTamanhoErradoInvalido() {
        assertFalse(DocumentoFiscal.cnpjValido("12ABC34501DE3A"));
        assertFalse(DocumentoFiscal.cnpjValido("12ABC34501DE3"));
        assertFalse(DocumentoFiscal.cnpjValido("00000000000000"));
    }

    @Test
    void cpf() {
        assertTrue(DocumentoFiscal.cpfValido("52998224725"));
        assertFalse(DocumentoFiscal.cpfValido("52998224724"));
        assertFalse(DocumentoFiscal.cpfValido("11111111111"));
        assertFalse(DocumentoFiscal.cpfValido("5299822472A"));
    }
}
