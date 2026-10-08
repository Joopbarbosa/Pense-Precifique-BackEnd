package com.penseprecifique.api.shared.dto;

import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #811 — a ficha técnica é opcional: ausente ou nula equivale a lista vazia, como o contrato OpenAPI declara. */
class ProdutoRequestTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void fichaTecnicaAusenteOuNulaViraListaVazia() throws Exception {
        for (String json : new String[] {"{\"nome\":\"Caderno\",\"tipo\":\"PRODUTO\"}",
                "{\"nome\":\"Caderno\",\"tipo\":\"PRODUTO\",\"fichaTecnica\":null}"}) {
            ProdutoRequest pedido = mapper.readValue(json, ProdutoRequest.class);

            assertNotNull(pedido.getFichaTecnica(), json);
            assertTrue(pedido.getFichaTecnica().isEmpty(), json);
        }
    }
}
