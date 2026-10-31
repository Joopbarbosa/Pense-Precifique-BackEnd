package com.penseprecifique.api.shared.validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaixaDataPermitidaTest {

    @Test
    void aceitaOsDoisLimitesERecusaDatasForaDaFaixaOuNulas() {
        assertEquals("As datas precisam estar entre 01/01/2000 e 31/12/2100.", FaixaDataPermitida.MENSAGEM);
        assertFalse(FaixaDataPermitida.contem(LocalDate.of(1999, 12, 31)));
        assertTrue(FaixaDataPermitida.contem(LocalDate.of(2000, 1, 1)));
        assertTrue(FaixaDataPermitida.contem(LocalDate.of(2100, 12, 31)));
        assertFalse(FaixaDataPermitida.contem(LocalDate.of(2101, 1, 1)));
        assertFalse(FaixaDataPermitida.contem(null));
    }
}
