package com.penseprecifique.api.producao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.18.0 (#1169, #1184, #1191) — entrada hostil dos endpoints de juntar produções: candidatas-juntar, do-periodo e
 * juntar. Corpo malformado, tipo de conteúdo errado, ids inválidos, id de outra conta/inexistente, lista vazia, com
 * mais de 20 itens, com nulo ou repetida, datas extremas. Resposta sempre 4xx, nunca 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProducaoJuntarEntradaHostilHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        String email = "hostil-juntar-" + UUID.randomUUID() + "@teste.com";
        String corpo = mvc.perform(MockMvcRequestBuilders.post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(corpo);
        token = n.get("token").asText();
    }

    private int enviar(HttpMethod metodo, String caminho, String tipo, String corpo) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(metodo, caminho).header("Authorization", "Bearer " + token);
        if (tipo != null) b.contentType(tipo);
        if (corpo != null) b.content(corpo);
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    private void esperar4xx(List<String> falhas, String rotulo, int obtido) {
        if (obtido < 400 || obtido >= 500) falhas.add(rotulo + " → " + obtido);
    }

    @Test
    void juntarComCorpoHostilNuncaDa500() throws Exception {
        String alvo = "/producoes/" + UUID.randomUUID() + "/juntar";
        String json = MediaType.APPLICATION_JSON_VALUE;
        String vinteUm = IntStream.range(0, 21).mapToObj(i -> "\"" + UUID.randomUUID() + "\"").collect(Collectors.joining(","));
        String repetido = UUID.randomUUID().toString();
        List<String> falhas = new ArrayList<>();
        esperar4xx(falhas, "corpo vazio", enviar(HttpMethod.POST, alvo, json, ""));
        esperar4xx(falhas, "sem corpo", enviar(HttpMethod.POST, alvo, json, null));
        esperar4xx(falhas, "json quebrado", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":["));
        esperar4xx(falhas, "texto puro", enviar(HttpMethod.POST, alvo, MediaType.TEXT_PLAIN_VALUE, "producaoOutraIds"));
        esperar4xx(falhas, "xml", enviar(HttpMethod.POST, alvo, MediaType.APPLICATION_XML_VALUE, "<a/>"));
        esperar4xx(falhas, "lista vazia", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":[]}"));
        esperar4xx(falhas, "lista nula", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":null}"));
        esperar4xx(falhas, "item nulo", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":[null]}"));
        esperar4xx(falhas, "id não uuid", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":[\"abc\"]}"));
        esperar4xx(falhas, "item número", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":[123]}"));
        esperar4xx(falhas, "lista é objeto", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":{}}"));
        esperar4xx(falhas, "21 itens", enviar(HttpMethod.POST, alvo, json, "{\"producaoOutraIds\":[" + vinteUm + "]}"));
        esperar4xx(falhas, "id repetido", enviar(HttpMethod.POST, alvo, json,
                "{\"producaoOutraIds\":[\"" + repetido + "\",\"" + repetido + "\"]}"));
        esperar4xx(falhas, "id de outra conta ou inexistente", enviar(HttpMethod.POST, alvo, json,
                "{\"producaoOutraIds\":[\"" + UUID.randomUUID() + "\"]}"));
        esperar4xx(falhas, "data inválida", enviar(HttpMethod.POST, alvo, json,
                "{\"producaoOutraIds\":[\"" + UUID.randomUUID() + "\"],\"dataInicio\":\"31/12/2026\"}"));
        esperar4xx(falhas, "data extrema", enviar(HttpMethod.POST, alvo, json,
                "{\"producaoOutraIds\":[\"" + UUID.randomUUID() + "\"],\"dataInicio\":\"+999999999-01-01\"}"));
        esperar4xx(falhas, "data é texto", enviar(HttpMethod.POST, alvo, json,
                "{\"producaoOutraIds\":[\"" + UUID.randomUUID() + "\"],\"dataTerminoPrevista\":\"amanhã\"}"));
        esperar4xx(falhas, "id da produção inválido na rota", enviar(HttpMethod.POST, "/producoes/abc/juntar", json,
                "{\"producaoOutraIds\":[\"" + UUID.randomUUID() + "\"]}"));
        assertTrue(falhas.isEmpty(), "juntar: " + falhas);
    }

    @Test
    void candidatasEDoPeriodoComIdHostilNuncaDa500() throws Exception {
        List<String> falhas = new ArrayList<>();
        esperar4xx(falhas, "candidatas id inexistente", enviar(HttpMethod.GET, "/producoes/" + UUID.randomUUID() + "/candidatas-juntar", null, null));
        esperar4xx(falhas, "candidatas id inválido", enviar(HttpMethod.GET, "/producoes/abc/candidatas-juntar", null, null));
        esperar4xx(falhas, "do-periodo sem orcamentoId", enviar(HttpMethod.GET, "/producoes/do-periodo", null, null));
        esperar4xx(falhas, "do-periodo orcamentoId vazio", enviar(HttpMethod.GET, "/producoes/do-periodo?orcamentoId=", null, null));
        esperar4xx(falhas, "do-periodo orcamentoId inválido", enviar(HttpMethod.GET, "/producoes/do-periodo?orcamentoId=abc", null, null));
        esperar4xx(falhas, "do-periodo orcamentoId inexistente", enviar(HttpMethod.GET, "/producoes/do-periodo?orcamentoId=" + UUID.randomUUID(), null, null));
        esperar4xx(falhas, "do-periodo injeção", enviar(HttpMethod.GET, "/producoes/do-periodo?orcamentoId=%27%20OR%201%3D1--", null, null));
        assertTrue(falhas.isEmpty(), "consultas: " + falhas);
    }

    @Test
    void semTokenDa401() throws Exception {
        int s = mvc.perform(MockMvcRequestBuilders.get("/producoes/do-periodo?orcamentoId=" + UUID.randomUUID()))
                .andReturn().getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "sem token → " + s);
    }
}
