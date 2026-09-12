package com.penseprecifique.api.shared.exception;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OP #455 — Prova do Bug (achado residual do gate seguranca-resiliencia, Fase 5/Schemathesis,
 * ao validar #421: contagem de 500 caiu de 87/87 para 1/87, este é o 1 residual).
 *
 * <p>Causa raiz confirmada via log real do container (não é a allowlist de
 * {@code PageableOrdenacaoResolver} — o request nem chega lá): um valor de {@code sort} com
 * sequência percent-encoded malformada (ex. {@code %v}, hex inválido após {@code %}) faz
 * {@code org.springframework.util.StringUtils.uriDecode} lançar {@code IllegalArgumentException}
 * dentro de {@code SortHandlerMethodArgumentResolver}, ANTES do controller — não era coberto por
 * nenhum dos handlers existentes (nem o novo de #421, que trata
 * {@code MethodArgumentTypeMismatchException}, tipo diferente), caindo no handler genérico.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SortMalformadoIT {

    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String tokenParaNovoUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("sort-malformado-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        return jwtTokenProvider.generateToken(usuario);
    }

    @Test
    void sortComPercentEncodingInvalidoRetorna400EmVezDe500() throws Exception {
        String token = tokenParaNovoUsuario();

        // "%v" não é uma sequência percent-encoded válida (hex inválido após '%') — passado cru
        // na URI (não via .param(), que re-escaparia o valor e mascararia o bug).
        mockMvc.perform(get("/clientes?sort=%v")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
