package com.penseprecifique.api.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** V0.18.0 (#1034, DT-NOVA-2) — rotas da Agenda e entrada hostil: período inválido, parâmetros malformados e página absurda nunca viram 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AgendaHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "agenda-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private int status(String token, String url) throws Exception {
        var req = get(url);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void rotasRespondemParaContaSemCompromissos() throws Exception {
        String t = token();
        MvcResult lista = mvc.perform(get("/agenda/compromissos?de=2026-10-12&ate=2026-10-18").header("Authorization", "Bearer " + t)).andReturn();
        assertEquals(200, lista.getResponse().getStatus());
        assertEquals(0, json.readTree(lista.getResponse().getContentAsString()).size());
        JsonNode resumo = json.readTree(mvc.perform(get("/agenda/resumo").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString());
        assertEquals(0, resumo.get("atrasados").asInt());
        assertEquals(0, resumo.get("hoje").asInt());
        JsonNode atrasados = json.readTree(mvc.perform(get("/agenda/atrasados").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString());
        assertEquals(0, atrasados.get("totalElements").asInt());
    }

    /** Entrada hostil: período ausente, malformado, invertido, enorme ou fora de 2000–2100 → 400 (nunca 500). */
    @Test
    void periodoHostilDevolve400() throws Exception {
        String t = token();
        String[] urls = {
                "/agenda/compromissos", "/agenda/compromissos?de=2026-10-12", "/agenda/compromissos?ate=2026-10-12",
                "/agenda/compromissos?de=abc&ate=2026-10-12", "/agenda/compromissos?de=2026-13-45&ate=2026-10-12",
                "/agenda/compromissos?de=2026-10-12&ate=2026-10-01", "/agenda/compromissos?de=1999-12-31&ate=2000-01-02",
                "/agenda/compromissos?de=2100-12-01&ate=2101-01-02", "/agenda/compromissos?de=2026-01-01&ate=2026-12-31",
                "/agenda/compromissos?de=-999999999-01-01&ate=2026-10-12", "/agenda/compromissos?de=2026-10-12&ate=%00",
        };
        for (String url : urls) {
            assertEquals(400, status(t, url), url);
        }
    }

    /** Entrada hostil na paginação dos atrasados: valores negativos, zero, gigantes ou texto nunca derrubam a rota. */
    @Test
    void paginacaoHostilNaoDa500() throws Exception {
        String t = token();
        String[] urls = {
                "/agenda/atrasados?page=-1", "/agenda/atrasados?size=0", "/agenda/atrasados?size=-5", "/agenda/atrasados?size=100000",
                "/agenda/atrasados?page=999999999", "/agenda/atrasados?page=abc", "/agenda/atrasados?size=abc",
        };
        for (String url : urls) {
            int s = status(t, url);
            assertTrue(s < 500, url + " -> " + s);
        }
    }

    @Test
    void semTokenNaoPassa() throws Exception {
        int s = status(null, "/agenda/resumo");
        assertTrue(s == 401 || s == 403, "sem token: " + s);
    }
}
