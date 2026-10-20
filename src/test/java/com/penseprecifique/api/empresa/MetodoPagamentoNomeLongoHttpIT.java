package com.penseprecifique.api.empresa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #1028 — método de pagamento "Outro" com nome acima de 100 caracteres é recusado com 400 e a mensagem no campo, nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MetodoPagamentoNomeLongoHttpIT {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void nomeComMaisDe100CaracteresRetorna400ENoLimiteSalva() throws Exception {
        Usuario u = usuarioRepository.save(Usuario.builder().email("metodo-longo-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        String t = jwtTokenProvider.generateToken(u);

        mockMvc.perform(post("/configuracoes/metodos-pagamento").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"OUTRO\",\"nome\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nome").value("O nome do método de pagamento pode ter no máximo 100 caracteres"));

        mockMvc.perform(post("/configuracoes/metodos-pagamento").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"OUTRO\",\"nome\":\"" + "x".repeat(100) + "\"}"))
                .andExpect(status().isOk());
    }
}
