package com.penseprecifique.api.agenda;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** V0.18.0 (#1181) — entrada hostil nas rotas de movimento da Agenda: corpo malformado, tipo inválido, data absurda, id de outra conta. Nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AgendaMovimentoHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "agenda-mov-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private int status(String token, String url, String tipo, String corpo) throws Exception {
        var req = post(url);
        if (tipo != null) req = req.contentType(tipo);
        if (corpo != null) req = req.content(corpo);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void entradaHostilNuncaDa500() throws Exception {
        String t = token();
        String id = UUID.randomUUID().toString();
        String[] corpos = {
                null, "", "{", "[]", "null", "{}", "texto",
                "{\"tipo\":\"INEXISTENTE\",\"referenciaId\":\"" + id + "\",\"novaData\":\"2026-10-30\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"nao-e-uuid\",\"novaData\":\"2026-10-30\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"" + id + "\",\"novaData\":\"2026-13-45\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"" + id + "\",\"novaData\":\"-999999999-01-01\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"" + id + "\",\"novaData\":\"+999999999-12-31\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"" + id + "\",\"novaData\":\"1900-01-01\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"" + id + "\",\"novaData\":\"2101-01-01\"}",
                "{\"tipo\":\"ENTREGA_ESTIMADA\",\"referenciaId\":\"" + id + "\",\"novaData\":\"2026-10-30\"}",
                "{\"tipo\":\"SALDO_A_RECEBER\",\"referenciaId\":\"" + id + "\",\"novaData\":\"2026-10-30\"}",
                "{\"tipo\":\"AVULSO\",\"referenciaId\":\"" + id + "\",\"novaData\":\"2026-10-30\"}",
                "{\"tipo\":null,\"referenciaId\":null,\"novaData\":null}",
        };
        for (String url : new String[]{"/agenda/movimentos/simular", "/agenda/movimentos"}) {
            for (String corpo : corpos) {
                int s = status(t, url, MediaType.APPLICATION_JSON_VALUE, corpo);
                assertTrue(s >= 400 && s < 500, url + " " + corpo + " -> " + s);
            }
            int s = status(t, url, MediaType.TEXT_PLAIN_VALUE, "{}");
            assertTrue(s >= 400 && s < 500, url + " text/plain -> " + s);
        }
    }

    @Test
    void idDeOutraContaDa404EComTokenAusenteNaoPassa() throws Exception {
        String t = token();
        String corpo = "{\"tipo\":\"RECEBIMENTO_COMPRA\",\"referenciaId\":\"" + UUID.randomUUID() + "\",\"novaData\":\"2026-10-30\"}";
        assertEquals(404, status(t, "/agenda/movimentos/simular", MediaType.APPLICATION_JSON_VALUE, corpo));
        int s = status(null, "/agenda/movimentos", MediaType.APPLICATION_JSON_VALUE, corpo);
        assertTrue(s == 401 || s == 403, "sem token: " + s);
    }
}
