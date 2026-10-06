package com.penseprecifique.api.compra.nota;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.16.0 (#723, DT-NOVA-9) — política de portal fiscal permitido para o link do comprovante, sem banco. */
class PortalFiscalPermitidoTest {

    private final PortalFiscalPermitido portais = new PortalFiscalPermitido(List.of("www.nfce.fazenda.sp.gov.br"));

    @Test
    void aceitaOPortalDeSaoPauloEmHttps() {
        assertTrue(portais.permitido("https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=123|2|1|1|AB"));
        assertTrue(portais.permitido("HTTPS://WWW.NFCE.FAZENDA.SP.GOV.BR:443/qrcode?p=1"));
    }

    @Test
    void recusaEsquemaHostPortaECredencialForaDaPolitica() {
        assertFalse(portais.permitido("http://www.nfce.fazenda.sp.gov.br/qrcode?p=1"));
        assertFalse(portais.permitido("https://exemplo.interno/admin"));
        assertFalse(portais.permitido("https://www.nfce.fazenda.sp.gov.br.malicioso.com/qrcode"));
        assertFalse(portais.permitido("https://www.nfce.fazenda.sp.gov.br:8443/qrcode"));
        assertFalse(portais.permitido("https://usuario:senha@www.nfce.fazenda.sp.gov.br/qrcode"));
        assertFalse(portais.permitido("https://localhost/qrcode"));
        assertFalse(portais.permitido("não é uma url"));
    }
}
