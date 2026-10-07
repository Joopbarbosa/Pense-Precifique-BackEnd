package com.penseprecifique.api.shared.texto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextoNormalizadoTest {

    @Test
    void removeAcentoMinusculaEEspacosDasPontas() {
        assertEquals("acucar refinado", TextoNormalizado.semAcentoMinusculo("  Açúcar Refinado "));
        assertEquals("pao de acucar", TextoNormalizado.semAcentoMinusculo("PÃO DE AÇÚCAR"));
    }

    @Test
    void espacosDoMeioFicamNaPrimeiraFormaEViramUmNaSegunda() {
        assertEquals("papel   couche", TextoNormalizado.semAcentoMinusculo("Papel   Couché"));
        assertEquals("papel couche 250g", TextoNormalizado.semAcentoMinusculoEspacoUnico("  PAPEL   Couché \t 250g "));
    }

    @Test
    void nuloEVazioViramVazio() {
        assertEquals("", TextoNormalizado.semAcentoMinusculo(null));
        assertEquals("", TextoNormalizado.semAcentoMinusculoEspacoUnico(null));
        assertEquals("", TextoNormalizado.semAcentoMinusculo("   "));
    }

    @Test
    void naoMexeEmPontuacaoENumeros() {
        assertEquals("c/100 a4", TextoNormalizado.semAcentoMinusculo("C/100 A4"));
    }
}
