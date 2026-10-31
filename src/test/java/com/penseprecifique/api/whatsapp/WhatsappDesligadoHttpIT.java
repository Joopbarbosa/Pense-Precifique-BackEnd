package com.penseprecifique.api.whatsapp;

import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #1170 (RN-NOVA-32): com o recurso desligado (padrão), nada chama a Evolution. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WhatsappDesligadoHttpIT extends WhatsappTestBase {

    @DynamicPropertySource
    static void falso(DynamicPropertyRegistry registry) {
        FALSO.start();
        registry.add("pdf.microservice.base-url", () -> "http://localhost:" + FALSO.port());
        registry.add("evolution.base-url", () -> "http://localhost:" + FALSO.port());
    }

    @AfterAll
    static void parar() { FALSO.stop(); }

    @Test
    void statusInformaDesligadoEConexaoEEnvioRecusam() throws Exception {
        Conta c = registrar("wa-desligado");
        String auth = "Bearer " + c.token();
        mvc.perform(get("/whatsapp/status").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.habilitado").value(false));
        mvc.perform(post("/whatsapp/conexao").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entendiRiscos\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O envio por WhatsApp ainda não está disponível neste ambiente."));
        VendaCaixaResponseDTO v = vender(c);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O envio por WhatsApp ainda não está disponível neste ambiente."));
        FALSO.verify(0, postRequestedFor(urlPathMatching("/instance/.*")));
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/.*")));
    }
}
