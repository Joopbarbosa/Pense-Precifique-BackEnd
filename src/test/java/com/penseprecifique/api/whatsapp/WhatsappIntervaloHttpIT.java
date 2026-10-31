package com.penseprecifique.api.whatsapp;

import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #1170 (RN-NOVA-35): intervalo mínimo de 30 s entre envios da mesma conta. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WhatsappIntervaloHttpIT extends WhatsappTestBase {

    @DynamicPropertySource
    static void falso(DynamicPropertyRegistry registry) {
        FALSO.start();
        registry.add("pdf.microservice.base-url", () -> "http://localhost:" + FALSO.port());
        registry.add("evolution.base-url", () -> "http://localhost:" + FALSO.port());
        registry.add("evolution.habilitado", () -> "true");
    }

    @AfterAll
    static void parar() { FALSO.stop(); }

    @Test
    void segundoEnvioLogoEmSeguidaPedeParaAguardar() throws Exception {
        Conta c = contaConectada("wa-intervalo");
        VendaCaixaResponseDTO v = vender(c);
        String corpo = corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true");
        enviar(c, corpo).andExpect(status().isOk());
        enviar(c, corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern("Aguarde \\d+ segundos para enviar de novo\\.")));
        FALSO.verify(1, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }
}
