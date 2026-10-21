package com.penseprecifique.api.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V0.17.0 #1022 (CEN-NOVO-128, BLOQUEIO): a Início sem credencial é recusada com 401 e não devolve faturamento. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DashboardSemCredencialHttpIT {

    @Autowired MockMvc mockMvc;

    @Test
    void cen128SemTokenDevolve401SemValores() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.receitaMes").doesNotExist());
    }
}
