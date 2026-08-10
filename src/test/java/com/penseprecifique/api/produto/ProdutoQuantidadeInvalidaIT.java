package com.penseprecifique.api.produto;

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
 * OP #208 — Prova do Bug: campo BigDecimal (fichaTecnica[].quantidade) com valor não numérico
 * ("1/2") vindo do frontend deve virar 400 com mensagem amigável, não 500 genérico.
 * GlobalExceptionHandler não tratava HttpMessageNotReadableException/InvalidFormatException
 * antes desta correção, caindo no handler genérico de Exception.class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProdutoQuantidadeInvalidaIT {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String tokenParaNovoUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-quantidade-invalida-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        return jwtTokenProvider.generateToken(usuario);
    }

    @Test
    void quantidadeNaoNumericaNaFichaTecnicaRetorna400ComMensagemAmigavel() throws Exception {
        String token = tokenParaNovoUsuario();
        String body = """
                {
                  "nome": "Teste Bug 208",
                  "tipo": "PRODUTO",
                  "tempoProducao": 10,
                  "fichaTecnica": [
                    {"insumoId": "11111111-1111-1111-1111-111111111111", "quantidade": "1/2"}
                  ]
                }
                """;

        mockMvc.perform(post("/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Valor inválido para o campo 'quantidade' — informe um número decimal (ex: 0.5)."));
    }
}
