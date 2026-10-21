package com.penseprecifique.api.config;

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

/** V0.17.0 #964 (RN-NOVA-19, CEN-NOVO-56 a 58): telefone da empresa pela API — aceite e bloqueio no servidor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TelefoneEmpresaHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "tel-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return n.get("token").asText();
    }

    private MvcResult salvarWhatsapp(String t, String whatsapp) throws Exception {
        return mvc.perform(put("/empresa").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ateliê\",\"whatsapp\":" + json.writeValueAsString(whatsapp) + "}"))
                .andReturn();
    }

    @Test
    void nacionalEEstrangeiroSaoGravadosComoDigitados() throws Exception {
        String t = token();
        assertEquals(200, salvarWhatsapp(t, "(11) 91234-5678").getResponse().getStatus());
        assertEquals(200, salvarWhatsapp(t, "+55 11 91234-5678").getResponse().getStatus());
        mvc.perform(get("/empresa").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.whatsapp").value("+55 11 91234-5678"));
    }

    @Test
    void valorForaDosDoisFormatosRecebeTelefoneInvalidoESemGravar() throws Exception {
        String t = token();
        assertEquals(200, salvarWhatsapp(t, "(11) 91234-5678").getResponse().getStatus());
        for (String ruim : new String[]{"abc", "(11) 1234", "912345678", "+55 11"}) {
            mvc.perform(put("/empresa").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Ateliê\",\"whatsapp\":" + json.writeValueAsString(ruim) + "}"))
                    .andExpect(jsonPath("$.fieldErrors.whatsapp").value("Telefone inválido"));
        }
        for (String longo : new String[]{"+55 11 91234-5678 9999999999", "9".repeat(40)}) {
            assertEquals(400, salvarWhatsapp(t, longo).getResponse().getStatus());
        }
        mvc.perform(get("/empresa").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.whatsapp").value("(11) 91234-5678"));
    }
}
