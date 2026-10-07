package com.penseprecifique.api.shared.exception;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #660 — regressão HTTP para as três rotas que retornavam 500 com corpo não multipart. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UploadNaoMultipartIT {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void corpoJsonEmUploadRetorna400NasTresRotas() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("upload-nao-multipart-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        String token = jwtTokenProvider.generateToken(usuario);
        String id = UUID.randomUUID().toString();

        for (String rota : new String[] {
                "/empresa/logo",
                "/produtos/" + id + "/foto",
                "/catalogos/" + id + "/itens/" + id + "/foto"
        }) {
            mockMvc.perform(post(rota)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(
                            "Arquivo não enviado corretamente. Envie o arquivo como formulário multipart."));
        }
    }

    @Test
    void parametroObrigatorioAusenteNoPreviewRetorna400() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("preview-sem-produto-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());

        mockMvc.perform(get("/producoes/preview")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateToken(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Parâmetro obrigatório ausente: 'produtoId'."));
    }
}
