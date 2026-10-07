package com.penseprecifique.api.infra.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** #784 e #785 — filtros de entrada: NUL na consulta é 400; método fora do contrato é 405 com Allow, ambos em JSON. */
class FiltrosDeEntradaTest {

    @Test
    void metodoForaDoContratoRetorna405ComAllowEJson() throws Exception {
        for (String metodo : new String[] {"TRACE", "PROPFIND", "QUERY", "LINK"}) {
            FilterChain cadeia = mock(FilterChain.class);
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            new MetodoForaDoContratoFilter().doFilter(new MockHttpServletRequest(metodo, "/orcamentos"), resposta, cadeia);

            assertEquals(405, resposta.getStatus(), metodo);
            assertTrue(resposta.getHeader("Allow").contains("GET"), metodo);
            assertTrue(resposta.getContentType().startsWith("application/json"), metodo);
            assertTrue(resposta.getContentAsString().contains("\"status\":405"), metodo);
            verify(cadeia, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void metodosDoContratoPassam() throws Exception {
        for (String metodo : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"}) {
            FilterChain cadeia = mock(FilterChain.class);
            MockHttpServletRequest requisicao = new MockHttpServletRequest(metodo, "/orcamentos");
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            new MetodoForaDoContratoFilter().doFilter(requisicao, resposta, cadeia);
            verify(cadeia).doFilter(requisicao, resposta);
        }
    }

    @Test
    void consultaComNulRetorna400EJson() throws Exception {
        for (String consulta : new String[] {"busca=x%00y", "busca=x\u0000y", "a=1&%00=2"}) {
            MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/clientes");
            requisicao.setQueryString(consulta);
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            new ConsultaSemNulFilter().doFilter(requisicao, resposta, mock(FilterChain.class));

            assertEquals(400, resposta.getStatus(), consulta);
            assertTrue(resposta.getContentAsString().contains("Os parâmetros da consulta são inválidos."), consulta);
        }
    }

    @Test
    void consultaNormalPassa() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/clientes");
        requisicao.setQueryString("busca=ana%20maria&page=0");
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        FilterChain cadeia = mock(FilterChain.class);
        new ConsultaSemNulFilter().doFilter(requisicao, resposta, cadeia);

        verify(cadeia).doFilter(requisicao, resposta);
    }
}
