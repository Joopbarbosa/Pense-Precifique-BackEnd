package com.penseprecifique.api.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** V0.17.0 #989 (RN-NOVA-29, entrada hostil): a consulta de evolução de preço de vários insumos nunca responde 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EvolucaoPrecoInsumosHttpIT {

    private static final String SENHA = "senha-valida-123";
    private static final String ROTA = "/dashboard/cadastros/evolucao-preco-insumos";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "evo-" + UUID.randomUUID() + "@studio.com";
        String corpo = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(corpo);
        return n.get("token").asText();
    }

    private int status(String t, String query) throws Exception {
        var req = get(ROTA + query);
        if (t != null) req = req.header("Authorization", "Bearer " + t);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void entradaInvalidaDaErroDeClienteNuncaErroInterno() throws Exception {
        String t = token();
        String id = UUID.randomUUID().toString();
        assertEquals(400, status(t, ""), "sem insumos");
        assertEquals(400, status(t, "?insumoIds="), "lista vazia");
        assertEquals(400, status(t, "?insumoIds=abc"), "id mal formado");
        String seis = "?insumoIds=" + String.join("&insumoIds=", java.util.Collections.nCopies(1, id)) + "&insumoIds=" + UUID.randomUUID()
                + "&insumoIds=" + UUID.randomUUID() + "&insumoIds=" + UUID.randomUUID() + "&insumoIds=" + UUID.randomUUID() + "&insumoIds=" + UUID.randomUUID();
        assertEquals(400, status(t, seis), "6 insumos");
        assertEquals(404, status(t, "?insumoIds=" + id), "insumo que não existe para esta conta");
        assertEquals(400, status(t, "?insumoIds=" + id + "&de=2026-13-45"), "data impossível");
        assertEquals(400, status(t, "?insumoIds=" + id + "&de=2026-10-31&ate=2026-10-01"), "data inicial maior que a final");
    }

    @Test
    void semCredencialDa401() throws Exception {
        assertEquals(401, status(null, "?insumoIds=" + UUID.randomUUID()));
    }
}
