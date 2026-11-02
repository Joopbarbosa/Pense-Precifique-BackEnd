package com.penseprecifique.api.shared.texto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** #699 (RN-NOVA-PP699-1) — forma canônica do e-mail. */
class EmailNormalizerTest {

    @Test
    void minusculasEAparado() {
        assertEquals("a@x.com", EmailNormalizer.normalizar("A@X.com"));
        assertEquals("ana.silva@studio.com", EmailNormalizer.normalizar("  Ana.Silva@Studio.COM \t"));
    }

    @Test
    void unicodeUsaLocaleRaizENaoDoServidor() {
        assertEquals("josé@café.com", EmailNormalizer.normalizar("JOSÉ@CAFÉ.com"));
        // "I" maiúsculo vira "i" comum (não "ı" turco), seja qual for o idioma da máquina
        assertEquals("info@x.com", EmailNormalizer.normalizar("INFO@x.com"));
    }

    @Test
    void nuloContinuaNuloEVazioContinuaVazio() {
        assertNull(EmailNormalizer.normalizar(null));
        assertEquals("", EmailNormalizer.normalizar("   "));
    }
}
