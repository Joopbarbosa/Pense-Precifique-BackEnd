package com.penseprecifique.api.dashboard;

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

/** V0.18.0 (#1040, RN-NOVA-1, CEN-NOVO-2) — o Dashboard geral deixou de existir; os dashboards de Vendas e de Cadastros seguem. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DashboardGeralRemovidoIT {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "dash-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"senha-valida-123\",\"confirmarSenha\":\"senha-valida-123\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void dashboardGeralNaoExisteMais() throws Exception {
        String t = token();
        int s = mvc.perform(get("/dashboard").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus();
        assertTrue(s == 404 || s == 405, "GET /dashboard -> " + s);
    }

    @Test
    void dashboardsDeVendasECadastrosSeguem() throws Exception {
        String t = token();
        assertEquals(200, mvc.perform(get("/dashboard/vendas").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/dashboard/cadastros").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus());
    }
}
