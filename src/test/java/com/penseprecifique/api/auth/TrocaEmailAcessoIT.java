package com.penseprecifique.api.auth;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #334 (RN-NOVA-1, DT-NOVA-13) — CEN-NOVO-2 a 4 e entrada hostil. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TrocaEmailAcessoIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String cadastrar(String email) throws Exception {
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return token(r);
    }

    private String token(MvcResult r) throws Exception {
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return n.get("token").asText();
    }

    private MvcResult trocar(String token, String corpo) throws Exception {
        return mvc.perform(put("/usuarios/me/email").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private static String corpo(String novo, String senha) {
        return "{\"novoEmail\":\"" + novo + "\",\"senhaAtual\":\"" + senha + "\"}";
    }

    private int codigo(MvcResult r) { return r.getResponse().getStatus(); }

    @Test
    void trocaComSucessoDevolveTokenNovoEInvalidaOAntigo() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String antigo = "ana-" + sufixo + "@studio.com";
        String novo = "ana.nova-" + sufixo + "@studio.com";
        String tokenAntigo = cadastrar(antigo);

        MvcResult r = trocar(tokenAntigo, corpo(novo, SENHA));
        assertEquals(200, codigo(r));
        String tokenNovo = token(r);
        assertEquals(novo, json.readTree(r.getResponse().getContentAsString()).get("email").asText());

        mvc.perform(get("/usuarios/me").header("Authorization", "Bearer " + tokenNovo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(novo));
        assertEquals(401, mvc.perform(get("/usuarios/me").header("Authorization", "Bearer " + tokenAntigo))
                .andReturn().getResponse().getStatus());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + novo + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk());
        assertEquals(400, mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + antigo + "\",\"senha\":\"" + SENHA + "\"}"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void senhaIncorretaEBloqueio() throws Exception {
        String e = "senha-" + UUID.randomUUID() + "@studio.com";
        String t = cadastrar(e);
        MvcResult r = trocar(t, corpo("outro-" + UUID.randomUUID() + "@studio.com", "123"));
        assertEquals(400, codigo(r));
        assertEquals("Senha atual incorreta", json.readTree(r.getResponse().getContentAsString()).get("message").asText());
        mvc.perform(get("/usuarios/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.email").value(e));
    }

    @Test
    void emailDeOutraContaIgualAoAtualOuInvalidoBloqueiam() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String outra = "bia-" + sufixo + "@loja.com";
        cadastrar(outra);
        String e = "ana-" + sufixo + "@studio.com";
        String t = cadastrar(e);

        assertMensagem(trocar(t, corpo(outra, SENHA)), "E-mail já cadastrado");
        assertMensagem(trocar(t, corpo(e, SENHA)), "O novo e-mail precisa ser diferente do atual");
        MvcResult invalido = trocar(t, corpo("ana.studio.com", SENHA));
        assertEquals(400, codigo(invalido));
        assertEquals("E-mail inválido", json.readTree(invalido.getResponse().getContentAsString())
                .get("fieldErrors").get("novoEmail").asText());
        mvc.perform(get("/usuarios/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.email").value(e));
    }

    @Test
    void entradaHostilNuncaDa500() throws Exception {
        String t = cadastrar("hostil-" + UUID.randomUUID() + "@studio.com");
        String longo = "a".repeat(400) + "@studio.com";

        assertEquals(400, codigo(trocar(t, corpo(longo, SENHA))));
        assertEquals(400, codigo(trocar(t, "{")));
        assertEquals(400, codigo(trocar(t, "{}")));
        assertEquals(400, codigo(trocar(t, "{\"novoEmail\":123,\"senhaAtual\":[]}")));
        assertEquals(400, codigo(trocar(t, corpo("x@y.com", "s".repeat(5000)))));
        int tipo = mvc.perform(put("/usuarios/me/email").header("Authorization", "Bearer " + t)
                .contentType(MediaType.TEXT_PLAIN).content("x")).andReturn().getResponse().getStatus();
        assertEquals(415, tipo);
        assertEquals(401, mvc.perform(put("/usuarios/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("x@y.com", SENHA))).andReturn().getResponse().getStatus());
    }

    private void assertMensagem(MvcResult r, String esperada) throws Exception {
        assertEquals(400, codigo(r));
        assertEquals(esperada, json.readTree(r.getResponse().getContentAsString()).get("message").asText());
    }
}
