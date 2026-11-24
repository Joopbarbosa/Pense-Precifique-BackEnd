package com.penseprecifique.api.infra.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** #733 — origens do CORS vêm de variável de ambiente; vazio ou "*" nunca abrem para todas. */
class CorsOrigensTest {

    @Test
    void padraoQuandoNaoConfigurado() {
        assertEquals(List.of("http://localhost:3000"), SecurityConfig.origensPermitidas(null));
        assertEquals(List.of("http://localhost:3000"), SecurityConfig.origensPermitidas("  "));
    }

    @Test
    void listaSeparadaPorVirgulaComEspacos() {
        assertEquals(List.of("http://localhost:3000", "http://localhost:3100"),
                SecurityConfig.origensPermitidas("http://localhost:3000, http://localhost:3100 ,"));
    }

    @Test
    void asteriscoNaoLiberaTodasAsOrigens() {
        assertEquals(List.of("http://localhost:3000"), SecurityConfig.origensPermitidas("*"));
    }
}
