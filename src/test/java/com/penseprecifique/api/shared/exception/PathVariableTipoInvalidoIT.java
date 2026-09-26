package com.penseprecifique.api.shared.exception;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OP #421 — Prova do Bug (achado da skill seguranca-resiliencia, Fase 5/Schemathesis,
 * 87/87 operações devolvendo 500): endpoint com {@code @PathVariable UUID} recebendo um
 * valor que não é UUID válido (ex.: "0") lança {@code MethodArgumentTypeMismatchException},
 * que o {@link GlobalExceptionHandler} não tratava antes desta correção — caía no handler
 * genérico de {@code Exception.class} e devolvia 500 em vez de 400.
 *
 * RN-NOVA-1 (DECISOES_V0.9.0.md, destino final DECISOES_GLOBAIS.md): erro de validação de
 * parâmetro (tipo incompatível) deve devolver 400, nunca 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PathVariableTipoInvalidoIT {

    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String tokenParaNovoUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("path-variable-invalido-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        return jwtTokenProvider.generateToken(usuario);
    }

    @Test
    void inativarClienteComIdNaoUuidRetorna400EmVezDe500() throws Exception {
        String token = tokenParaNovoUsuario();

        mockMvc.perform(post("/clientes/0/inativar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getClienteComIdNaoUuidRetorna400EmVezDe500() throws Exception {
        String token = tokenParaNovoUsuario();

        mockMvc.perform(get("/clientes/nao-e-um-uuid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }
}
