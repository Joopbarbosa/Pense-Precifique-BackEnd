package com.penseprecifique.api.infra.config;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** #785 — erro devolvido pelo contêiner (sendError) sai no formato padrão, com o status original. */
class ErroPadraoControllerTest {

    private final ErroPadraoController controller = new ErroPadraoController();

    @Test
    void devolveOStatusDoContainerNoFormatoPadrao() {
        for (int status : new int[] {400, 401, 403, 404, 405, 415, 500, 501}) {
            MockHttpServletRequest requisicao = new MockHttpServletRequest();
            requisicao.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);

            var resposta = controller.erro(requisicao);

            assertEquals(status, resposta.getStatusCode().value());
            assertEquals(status, resposta.getBody().status());
        }
    }

    @Test
    void semStatusNoRequestVira500() {
        assertEquals(500, controller.erro(new MockHttpServletRequest()).getStatusCode().value());
    }
}
