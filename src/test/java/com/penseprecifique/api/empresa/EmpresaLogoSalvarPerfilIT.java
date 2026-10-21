package com.penseprecifique.api.empresa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Empresa;
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

/**
 * V0.17.0 #982 (RN-NOVA-27, DT-NOVA-22, CEN-NOVO-80 e 81): salvar o perfil da empresa nunca altera o logo gravado.
 * O logo só muda por envio ou remoção; o `logoUrl` da requisição de salvar o perfil é ignorado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EmpresaLogoSalvarPerfilIT {

    private static final String SENHA = "senha-valida-123";
    private static final String LOGO = "http://localhost:9000/test-pocket-only/empresa/abc/logo.png";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EmpresaRepository empresaRepository;
    private final ObjectMapper json = new ObjectMapper();

    /** Conta nova com empresa e o logo já gravado (como se o envio da imagem tivesse acontecido). */
    private String contaComLogo() throws Exception {
        String email = "logo-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        String token = n.get("token").asText();
        UUID usuarioId = usuarioRepository.findByEmail(email).orElseThrow().getId();
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId).orElseThrow();
        empresa.setLogoUrl(LOGO);
        empresaRepository.saveAndFlush(empresa);
        return token;
    }

    private MvcResult salvar(String token, String corpo) throws Exception {
        return mvc.perform(put("/empresa").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private void logoContinua(String token) throws Exception {
        mvc.perform(get("/empresa").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.logoUrl").value(LOGO));
    }

    @Test
    void salvarPerfilSemLogoNaRequisicaoMantemOLogoGravado() throws Exception {
        String t = contaComLogo();
        MvcResult r = salvar(t, "{\"nome\":\"Ateliê Ana Souza\",\"whatsapp\":\"(11) 91234-5678\"}");
        assertEquals(200, r.getResponse().getStatus());
        mvc.perform(get("/empresa").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.nome").value("Ateliê Ana Souza"))
                .andExpect(jsonPath("$.whatsapp").value("(11) 91234-5678"))
                .andExpect(jsonPath("$.logoUrl").value(LOGO));
    }

    @Test
    void logoUrlVazioExternoEnormeOuComCaractereNuloNaRequisicaoEIgnorado() throws Exception {
        String t = contaComLogo();
        String[] logos = {
                "\"\"",
                "null",
                "\"http://exemplo.com/outro.png\"",
                json.writeValueAsString("http://exemplo.com/" + "a".repeat(3000)),
                json.writeValueAsString("http://exemplo.com/\u0000.png"),
        };
        for (String logo : logos) {
            MvcResult r = salvar(t, "{\"nome\":\"Ateliê\",\"logoUrl\":" + logo + "}");
            assertEquals(200, r.getResponse().getStatus(), "logoUrl=" + logo.substring(0, Math.min(40, logo.length())));
            logoContinua(t);
        }
    }

    @Test
    void corpoMalformadoDa400EContentTypeErradoDa415SemMexerNoLogo() throws Exception {
        String t = contaComLogo();
        assertEquals(400, salvar(t, "{\"nome\":").getResponse().getStatus());
        assertEquals(415, mvc.perform(put("/empresa").header("Authorization", "Bearer " + t)
                .contentType(MediaType.TEXT_PLAIN).content("nome=Ateliê")).andReturn().getResponse().getStatus());
        logoContinua(t);
    }

    @Test
    void semCredencialDa401() throws Exception {
        assertEquals(401, mvc.perform(put("/empresa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ateliê\"}")).andReturn().getResponse().getStatus());
    }
}
