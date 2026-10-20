package com.penseprecifique.api.infra.config;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * V0.17.0 #1016 (segurança, Schemathesis): campo de texto com número ou booleano no corpo é recusado com 400 em vez de
 * gravar "false" ou "0" (coerção silenciosa do Jackson). Texto de verdade continua aceito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EntradaJsonTipoErradoHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "tipo-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private int enviar(String metodo, String caminho, String t, String corpo) throws Exception {
        var req = "PUT".equals(metodo) ? put(caminho) : post(caminho);
        return mvc.perform(req.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void campoDeTextoComBooleanoOuNumeroViraBloqueio() throws Exception {
        String t = token();
        assertEquals(400, enviar("POST", "/catalogos", t, "{\"nome\":false}"));
        assertEquals(400, enviar("POST", "/catalogos", t, "{\"nome\":123}"));
        assertEquals(400, enviar("POST", "/unidades-medida", t, "{\"nome\":\"Metro\",\"sigla\":false}"));
        assertEquals(400, enviar("PUT", "/empresa", t, "{\"nome\":\"Ateliê\",\"email\":\"a@b.com\",\"whatsapp\":\"\",\"endereco\":\"\",\"logoUrl\":false,\"horarios\":[]}"));
    }

    @Test
    void textoDeVerdadeContinuaAceito() throws Exception {
        String t = token();
        assertEquals(201, enviar("POST", "/catalogos", t, "{\"nome\":\"Natal 2026\"}"));
        assertEquals(201, enviar("POST", "/unidades-medida", t, "{\"nome\":\"Unidade " + UUID.randomUUID() + "\",\"sigla\":\"u" + (int) (Math.random() * 100000) + "\"}"));
    }
}
