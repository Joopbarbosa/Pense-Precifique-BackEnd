package com.penseprecifique.api.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unitários puros: normalização do número (RN-NOVA-27) e legendas padrão (RN-NOVA-30). */
class WhatsappLegendaENumeroTest {

    @Test
    void numeroBrasileiroGanhaPrefixo55() {
        assertEquals("5511912345678", NumeroWhatsapp.canonico("(11) 91234-5678"));
        assertEquals("551132345678", NumeroWhatsapp.canonico("11 3234-5678"));
        assertEquals("5511912345678", NumeroWhatsapp.canonico("+55 11 91234-5678"));
        assertEquals("5511912345678", NumeroWhatsapp.canonico("5511912345678"));
    }

    @Test
    void numeroInternacionalExigeMaisEEntre8e15Digitos() {
        assertEquals("351912345678", NumeroWhatsapp.canonico("+351 912 345 678"));
        assertEquals("12345678", NumeroWhatsapp.canonico("+1234 5678"));
        assertNull(NumeroWhatsapp.canonico("+1234567"));
        assertNull(NumeroWhatsapp.canonico("+1234567890123456"));
    }

    @Test
    void numeroInvalidoRetornaNulo() {
        for (String s : new String[]{null, "", " ", "abc", "123", "1e20", "11912345678x", "++5511912345678",
                "1".repeat(41), "123456789", "1234567890123456"}) {
            assertNull(NumeroWhatsapp.canonico(s), "[" + s + "]");
        }
    }

    @Test
    void legendasPorTipo() {
        assertEquals("Olá, Ana! Segue o orçamento nº 12. As condições de pagamento estão na página 2.",
                WhatsappEnvioService.legendaPadrao(TipoDocumentoWhatsapp.ORCAMENTO, 12, "Ana"));
        assertEquals("Segue o recibo nº 3.", WhatsappEnvioService.legendaPadrao(TipoDocumentoWhatsapp.RECIBO_SINAL, 3, null));
        assertEquals("Segue o comprovante do orçamento nº 4.", WhatsappEnvioService.legendaPadrao(TipoDocumentoWhatsapp.MULTA, 4, " "));
        assertEquals("Olá, Ana! Segue a compra nº 5.", WhatsappEnvioService.legendaPadrao(TipoDocumentoWhatsapp.COMPRA, 5, "Ana"));
        assertEquals("Segue a lista de compras LST-7.", WhatsappEnvioService.legendaPadrao(TipoDocumentoWhatsapp.LISTA_COMPRAS, 7, "Ana"));
        assertEquals("Olá, Ana! Segue o nosso catálogo.", WhatsappEnvioService.legendaPadrao(TipoDocumentoWhatsapp.CATALOGO, 1, "Ana"));
    }
}
