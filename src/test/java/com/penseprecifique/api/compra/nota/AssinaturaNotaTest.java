package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.16.0 (#683, DT-NOVA-9) — a assinatura liga a leitura ao rascunho e recusa qualquer alteração. */
class AssinaturaNotaTest {

    private static final UUID USUARIA = UUID.randomUUID();
    private static final String SEGREDO = "segredo-de-teste-do-pocket";

    private static NotaLida nota(String valor, String nome) {
        return new NotaLida(new NotaLida.Emitente("11222333000181", "Papelaria", "SP"), "1".repeat(44), "9", "1", "2026-10-01",
                new BigDecimal(valor), null, null,
                List.of(new NotaLida.Item(nome, new BigDecimal("2"), new BigDecimal(valor), null, null, "UN", null, null)),
                "NFCE_QR", "LEITOR_UF", false, "SP", "SP-1", List.of());
    }

    private static AssinaturaNota assinatura(Instant agora) {
        return new AssinaturaNota(SEGREDO, 120, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void notaIntactaPassaMesmoComEscalaDiferenteDeDecimal() {
        AssinaturaNota a = assinatura(Instant.parse("2026-10-04T12:00:00Z"));
        String token = a.assinar(USUARIA, nota("12.50", "Fita"), "abc");
        assertDoesNotThrow(() -> a.verificar(token, USUARIA, nota("12.5", "Fita"), "abc"));
        assertDoesNotThrow(() -> a.verificar(token, USUARIA, nota("12.500", "Fita"), "abc"));
    }

    @Test
    void alteracaoDeValorNomeUsuariaOuComprovanteRecusa() {
        AssinaturaNota a = assinatura(Instant.parse("2026-10-04T12:00:00Z"));
        String token = a.assinar(USUARIA, nota("12.50", "Fita"), "abc");
        assertThrows(BusinessException.class, () -> a.verificar(token, USUARIA, nota("1.50", "Fita"), "abc"));
        assertThrows(BusinessException.class, () -> a.verificar(token, USUARIA, nota("12.50", "Fita2"), "abc"));
        assertThrows(BusinessException.class, () -> a.verificar(token, UUID.randomUUID(), nota("12.50", "Fita"), "abc"));
        assertThrows(BusinessException.class, () -> a.verificar(token, USUARIA, nota("12.50", "Fita"), "outro"));
        assertThrows(BusinessException.class, () -> a.verificar("lixo", USUARIA, nota("12.50", "Fita"), "abc"));
        assertThrows(BusinessException.class, () -> a.verificar(null, USUARIA, nota("12.50", "Fita"), "abc"));
    }

    @Test
    void assinaturaExpiradaRecusa() {
        String token = assinatura(Instant.parse("2026-10-04T12:00:00Z")).assinar(USUARIA, nota("12.50", "Fita"), null);
        AssinaturaNota depois = assinatura(Instant.parse("2026-10-04T14:01:00Z"));
        assertThrows(BusinessException.class, () -> depois.verificar(token, USUARIA, nota("12.50", "Fita"), null));
    }

    @Test
    void trocarOExpiraEmDoTokenInvalidaOHmac() {
        AssinaturaNota a = assinatura(Instant.parse("2026-10-04T12:00:00Z"));
        String token = a.assinar(USUARIA, nota("12.50", "Fita"), null);
        String adulterado = (Long.parseLong(token.substring(0, token.indexOf('.'))) + 99999) + token.substring(token.indexOf('.'));
        assertThrows(BusinessException.class, () -> a.verificar(adulterado, USUARIA, nota("12.50", "Fita"), null));
    }

    @Test
    void semSegredoConfiguradoNaoAssinaNemVerifica() {
        AssinaturaNota semSegredo = new AssinaturaNota("", 120, Clock.systemUTC());
        assertThrows(BusinessException.class, () -> semSegredo.assinar(USUARIA, nota("1", "A"), null));
        assertThrows(BusinessException.class, () -> semSegredo.verificar("1.x", USUARIA, nota("1", "A"), null));
    }
}
