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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #339 (RN-NOVA-3, DT-NOVA-14) — CEN-NOVO-5, 7, 8, 9 e entrada hostil. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CadastroComEmpresaIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private MvcResult cadastrar(String email, String nomeEmpresa) throws Exception {
        String empresa = nomeEmpresa == null ? "" : ",\"nomeEmpresa\":\"" + nomeEmpresa + "\"";
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\"" + empresa + "}"))
                .andReturn();
    }

    private String token(MvcResult r) throws Exception {
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return n.get("token").asText();
    }

    @Test
    void cadastroGravaOsNomeDaEmpresaSemLiberarOGateDoOnboarding() throws Exception {
        MvcResult r = cadastrar("emp-" + UUID.randomUUID() + "@studio.com", "Ateliê da Ana");
        assertEquals(201, r.getResponse().getStatus());
        String t = token(r);

        mvc.perform(get("/empresa").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ateliê da Ana"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.whatsapp").doesNotExist())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
        // o gate do onboarding continua pela ausência de ConfiguracaoPrecificacao (AUT-004)
        mvc.perform(get("/configuracoes/precificacao").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void passo1DoOnboardingAtualizaEmpresaComContatoETelefone() throws Exception {
        String t = token(cadastrar("emp2-" + UUID.randomUUID() + "@studio.com", "Ateliê da Ana"));
        mvc.perform(put("/empresa").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ateliê da Ana\",\"email\":\"contato@ateliedaana.com\",\"whatsapp\":\"(11) 91234-5678\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/empresa").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.email").value("contato@ateliedaana.com"))
                .andExpect(jsonPath("$.whatsapp").value("(11) 91234-5678"));
    }

    @Test
    void cadastroSemNomeDeEmpresaSegueValidoEPerfilNaoExiste() throws Exception {
        MvcResult r = cadastrar("sem-" + UUID.randomUUID() + "@studio.com", null);
        assertEquals(201, r.getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/empresa").header("Authorization", "Bearer " + token(r)))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void entradaHostilNuncaDa500() throws Exception {
        String longo = "a".repeat(300);
        assertEquals(400, cadastrar("h1-" + UUID.randomUUID() + "@studio.com", longo).getResponse().getStatus());
        // e-mail repetido não deixa empresa órfã nem 500
        String email = "dup-" + UUID.randomUUID() + "@studio.com";
        cadastrar(email, "Primeira");
        assertEquals(400, cadastrar(email, "Segunda").getResponse().getStatus());

        String t = token(cadastrar("h2-" + UUID.randomUUID() + "@studio.com", "Ateliê"));
        for (String corpo : new String[]{
                "{\"nome\":\"\"}", "{\"nome\":\"   \"}", "{\"nome\":\"x\",\"email\":\"sem-arroba\"}",
                "{\"nome\":\"x\",\"whatsapp\":\"" + "9".repeat(40) + "\"}",
                "{\"nome\":\"" + longo + "\"}", "{", "[]"}) {
            int codigo = mvc.perform(put("/empresa").header("Authorization", "Bearer " + t)
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus();
            assertTrue(codigo == 400, "corpo " + corpo + " deu " + codigo);
        }
        assertEquals(415, mvc.perform(put("/empresa").header("Authorization", "Bearer " + t)
                .contentType(MediaType.TEXT_PLAIN).content("x")).andReturn().getResponse().getStatus());
    }
}
