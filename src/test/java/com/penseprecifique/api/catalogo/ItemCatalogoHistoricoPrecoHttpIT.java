package com.penseprecifique.api.catalogo;

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

/** V0.18.0 (#961) — rota do histórico de preço do item e entrada hostil: id e paginação malformados, item de outra conta; nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ItemCatalogoHistoricoPrecoHttpIT {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "hist-item-http-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"senha-valida-123\",\"confirmarSenha\":\"senha-valida-123\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void historicoDeItemSemMudancasEIsolamentoEntradaHostil() throws Exception {
        String dono = token();
        String intruso = token();
        MvcResult c = mvc.perform(post("/catalogos").header("Authorization", "Bearer " + dono).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Festas\"}")).andReturn();
        String catalogoId = json.readTree(c.getResponse().getContentAsString()).get("id").asText();
        MvcResult i = mvc.perform(post("/insumos").header("Authorization", "Bearer " + dono).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Papel\",\"unidadeMedidaId\":null}")).andReturn();
        assertTrue(i.getResponse().getStatus() < 500);
        String base = "/catalogos/" + catalogoId + "/itens/" + UUID.randomUUID() + "/historico-preco";
        assertEquals(404, mvc.perform(get(base).header("Authorization", "Bearer " + dono)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get(base).header("Authorization", "Bearer " + intruso)).andReturn().getResponse().getStatus());
        for (String url : new String[] {"/catalogos/nao-uuid/itens/nao-uuid/historico-preco", base + "?page=-1", base + "?size=0",
                base + "?size=999999", base + "?page=abc"}) {
            int s = mvc.perform(get(url).header("Authorization", "Bearer " + dono)).andReturn().getResponse().getStatus();
            assertTrue(s < 500, url + " -> " + s);
        }
        int semToken = mvc.perform(get(base)).andReturn().getResponse().getStatus();
        assertTrue(semToken == 401 || semToken == 403, "sem token: " + semToken);
    }
}
