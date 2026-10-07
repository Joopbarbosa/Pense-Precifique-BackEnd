package com.penseprecifique.api.shared.exception;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.junit.jupiter.api.Test;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #762 (achado do Schemathesis no gate de segurança da V0.16.0) — requisição malformada nunca vira 500.
 * As exceções abaixo são as que o Tomcat e o Spring lançaram nas 10 respostas 500 reproduzidas com curl contra a
 * API de teste (multipart sem boundary, parâmetro de consulta com nome vazio, método e Content-Type não aceitos).
 */
class RequisicaoMalformadaTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void multipartSemBoundaryRetorna400() {
        MultipartException ex = new MultipartException("Failed to parse multipart servlet request",
                new FileUploadException("the request was rejected because no multipart boundary was found"));

        var resposta = handler.handleMultipartException(ex);

        assertEquals(400, resposta.getStatusCode().value());
        assertEquals("Arquivo não enviado corretamente. Tente novamente.", resposta.getBody().message());
    }

    @Test
    void parametroDeConsultaComNomeVazioRetorna400() {
        var resposta = handler.handleInvalidParameter(new InvalidParameterException("Invalid chunk [=null] ignored"));

        assertEquals(400, resposta.getStatusCode().value());
        assertEquals("Os parâmetros da consulta são inválidos.", resposta.getBody().message());
    }

    @Test
    void metodoNaoSuportadoRetorna405() {
        var resposta = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"));

        assertEquals(405, resposta.getStatusCode().value());
        assertEquals(405, resposta.getBody().status());
    }

    @Test
    void metodoNaoSuportadoListaOsPermitidosNoCabecalhoAllow() {
        var resposta = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET", "PUT")));

        assertEquals(405, resposta.getStatusCode().value());
        assertEquals("GET,PUT", String.join(",", resposta.getHeaders().getAllow().stream().map(Object::toString).toList()));
    }

    @Test
    void contentTypeNaoSuportadoRetorna415() {
        var resposta = handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("text/plain"));

        assertEquals(415, resposta.getStatusCode().value());
        assertEquals(415, resposta.getBody().status());
    }

    /** #767 — caminho inexistente é 404, não 500. */
    @Test
    void caminhoInexistenteRetorna404() {
        var resposta = handler.handleCaminhoInexistente(new NoResourceFoundException(HttpMethod.GET, "/rota-que-nao-existe", "rota-que-nao-existe"));

        assertEquals(404, resposta.getStatusCode().value());
        assertEquals("Endereço não encontrado.", resposta.getBody().message());
    }
}
