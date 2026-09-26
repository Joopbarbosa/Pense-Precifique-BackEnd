package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #483 — Prova do Bug: payload do fuzzing (Schemathesis) com texto acima do tamanho da coluna
 * (whatsapp VARCHAR(20)) ou com caractere nulo passava pela validação (ClienteRequest só tinha
 * {@code @NotBlank} em nome) e estourava no Postgres, caindo no handler genérico como 500.
 * #559 — observação acima de 500 caracteres também vira 400 no campo (RN-NOVA-18).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClienteValidacaoHttpIT {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String token() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("cli-validacao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        return jwtTokenProvider.generateToken(usuario);
    }

    private void postEsperando400NoCampo(String body, String campo) throws Exception {
        mockMvc.perform(post("/clientes")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors." + campo).exists());
    }

    @Test
    void whatsappNumericoLongoRetorna400() throws Exception {
        // número JSON vira String pelo Jackson: "-1.2345678901234567E308" tem 23 caracteres
        postEsperando400NoCampo("""
                {"nome": "Fuzz", "ehCliente": true, "whatsapp": -1.2345678901234567E308}
                """, "whatsapp");
    }

    @Test
    void caractereNuloNoNomeRetorna400() throws Exception {
        postEsperando400NoCampo("""
                {"nome": "Ana\\u0000Paula", "ehCliente": true}
                """, "nome");
    }

    @Test
    void observacoesCom501CaracteresRetorna400() throws Exception {
        postEsperando400NoCampo("{\"nome\": \"Ana\", \"ehCliente\": true, \"observacoes\": \""
                + "a".repeat(501) + "\"}", "observacoes");
    }

    @Test
    void observacoesCom500CaracteresSalva() throws Exception {
        mockMvc.perform(post("/clientes")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Ana\", \"ehCliente\": true, \"observacoes\": \""
                                + "a".repeat(500) + "\"}"))
                .andExpect(status().isCreated());
    }
}
