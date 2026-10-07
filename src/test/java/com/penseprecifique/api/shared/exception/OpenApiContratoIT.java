package com.penseprecifique.api.shared.exception;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #765 (triagem do Schemathesis da V0.16.0) — o OpenAPI descreve as respostas reais: erros padrão em toda operação,
 * campos que podem vir nulos no dashboard de compras e o mínimo de caracteres da justificativa de cancelamento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenApiContratoIT {

    @Autowired MockMvc mockMvc;

    private JsonNode docs() throws Exception {
        String corpo = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(corpo);
    }

    @Test
    void todaOperacaoDeclaraAsRespostasDeErroPadrao() throws Exception {
        JsonNode paths = docs().get("paths");
        int operacoes = 0;
        for (var caminho : paths.properties()) {
            for (var op : caminho.getValue().properties()) {
                if (!List.of("get", "post", "put", "patch", "delete").contains(op.getKey())) continue;
                operacoes++;
                JsonNode respostas = op.getValue().get("responses");
                for (String codigo : List.of("400", "401", "403", "404", "405", "415", "500")) {
                    assertTrue(respostas.has(codigo), caminho.getKey() + " " + op.getKey() + " sem resposta " + codigo);
                }
                assertEquals("#/components/schemas/ErrorResponseDTO",
                        respostas.get("500").get("content").get("application/json").get("schema").get("$ref").asString());
            }
        }
        assertTrue(operacoes > 100, "esperava mais de 100 operações, achou " + operacoes);
    }

    @Test
    void camposQuePodemVirNulosNoDashboardAceitamNulo() throws Exception {
        JsonNode schemas = docs().get("components").get("schemas");
        for (String campo : List.of("percentual", "percentualAnterior", "variacaoPercentual")) {
            assertAceitaNulo(schemas.get("Cmv").get("properties").get(campo), "Cmv." + campo);
        }
        assertAceitaNulo(schemas.get("Numero").get("properties").get("valor"), "Numero.valor");
        assertAceitaNulo(schemas.get("Numero").get("properties").get("variacaoPercentual"), "Numero.variacaoPercentual");
        assertAceitaNulo(schemas.get("Economia").get("properties").get("percentual"), "Economia.percentual");
    }

    @Test
    void justificativaDoCancelamentoDeclaraOMinimoDe30() throws Exception {
        JsonNode j = docs().get("components").get("schemas").get("CancelarProducaoRequest").get("properties").get("justificativa");
        assertEquals(30, j.get("minLength").asInt());
        assertEquals(500, j.get("maxLength").asInt());
    }

    @Test
    void erroPadraoDescreveOTimestampSemFusoEOsCamposOpcionais() throws Exception {
        JsonNode erro = docs().get("components").get("schemas").get("ErrorResponseDTO").get("properties");
        assertEquals("local-date-time", erro.get("timestamp").get("format").asString());
        for (String campo : List.of("fieldErrors", "titulo", "motivo", "comoResolver", "itens")) {
            assertAceitaNulo(erro.get(campo), "ErrorResponseDTO." + campo);
        }
    }

    @Test
    void maiorAumentoDoDashboardNaoMisturaRefComTipoNulo() throws Exception {
        JsonNode m = docs().get("components").get("schemas").get("DashboardComprasResponse").get("properties").get("maiorAumento");
        assertTrue(!m.has("$ref") && !m.has("type"), "ref e type na mesma propriedade: " + m);
        assertEquals(2, m.get("anyOf").size());
        assertAceitaNulo(m.get("anyOf").get(1), "maiorAumento");
    }

    private static void assertAceitaNulo(JsonNode propriedade, String nome) {
        String texto = propriedade.toString();
        assertTrue(texto.contains("\"null\"") || texto.contains("\"nullable\":true"), nome + " não aceita nulo: " + texto);
    }
}
