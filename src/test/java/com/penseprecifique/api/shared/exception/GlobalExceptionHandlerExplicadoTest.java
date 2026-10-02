package com.penseprecifique.api.shared.exception;

import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** V0.15.0 (#602, RN-NOVA-32/DT-NOVA-21) — erro explicado e compatibilidade com o erro simples. */
class GlobalExceptionHandlerExplicadoTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void erroExplicadoLevaTituloMotivoComoResolverEItens() {
        BusinessException ex = BusinessException.explicado("Compra incompleta", "Não foi possível confirmar a compra.",
                "Cada linha precisa de quantidade e preço.", "Complete as linhas apontadas.")
                .comItens(List.of("Linha 1 (Fita): informe o preço", "Linha 2 (Cola): informe a quantidade"));

        ErrorResponseDTO body = handler.handleBusinessException(ex).getBody();

        assertEquals(400, body.status());
        assertEquals("Não foi possível confirmar a compra.", body.message());
        assertEquals("Compra incompleta", body.titulo());
        assertEquals("Cada linha precisa de quantidade e preço.", body.motivo());
        assertEquals("Complete as linhas apontadas.", body.comoResolver());
        assertEquals(List.of("Linha 1 (Fita): informe o preço", "Linha 2 (Cola): informe a quantidade"), body.itens());
    }

    @Test
    void erroSimplesContinuaSoComMessage() {
        ErrorResponseDTO body = handler.handleBusinessException(new BusinessException("Qualquer regra")).getBody();

        assertEquals("Qualquer regra", body.message());
        assertNull(body.titulo());
        assertNull(body.motivo());
        assertNull(body.comoResolver());
        assertNull(body.itens());
    }

    @Test
    void falhaDeInfraestruturaMultipartContinuaSendoErroInterno() {
        MultipartException ex = new MultipartException(
                "Failed to parse multipart servlet request", new IOException("Falha no armazenamento temporário"));

        ErrorResponseDTO body = handler.handleMultipartException(ex).getBody();

        assertEquals(500, body.status());
        assertEquals("Erro interno do servidor", body.message());
    }
}
