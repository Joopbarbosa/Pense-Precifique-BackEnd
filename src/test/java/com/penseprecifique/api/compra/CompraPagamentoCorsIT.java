package com.penseprecifique.api.compra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #550 — achado da integração com o frontend (V0.15.0): {@code PATCH /compras/{id}/pagamento} é o
 * primeiro endpoint PATCH do sistema, e o CORS só liberava GET/POST/PUT/DELETE/OPTIONS. O preflight
 * do navegador voltava 403 e a tela nunca conseguia alterar o pagamento de uma compra confirmada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CompraPagamentoCorsIT {

    @Autowired MockMvc mockMvc;

    @Test
    void preflightDePatchDoFrontendEhAceito() throws Exception {
        mockMvc.perform(options("/compras/" + UUID.randomUUID() + "/pagamento")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }
}
