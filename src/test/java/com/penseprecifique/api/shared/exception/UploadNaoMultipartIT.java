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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /** #762 — método e Content-Type fora do contrato respondem 405 e 415, não 500. */
    @Test
    void metodoEContentTypeForaDoContratoNaoViram500() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("metodo-ct-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        String token = "Bearer " + jwtTokenProvider.generateToken(usuario);

        mockMvc.perform(put("/usuarios/me").header("Authorization", token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        // #767 — caminho inexistente com usuário autenticado é 404, não 500
        mockMvc.perform(get("/rota-que-nao-existe").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    /** #774 — lista inválida no corpo dos simuladores é 400 (era NullPointerException e HandlerMethodValidationException: 500). */
    @Test
    void simuladoresDeAlertaRecusamCorpoInvalidoComo400() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("simuladores-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        String token = "Bearer " + jwtTokenProvider.generateToken(usuario);

        for (String corpo : new String[] {"[{\"a\":{\"b\":false}}]", "[{}]", "[1,2]", "{}"}) {
            mockMvc.perform(post("/orcamentos/simular-alertas").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        for (String corpo : new String[] {
                "[{\"produtoId\":\"9d54b643-0e9c-34aa-a802-8648b1ffee65\"},{\"produtoId\":\"923867b7-3d23-45c3-bea2-b9dd6010c663\"}]",
                "[{}]", "[1]"}) {
            mockMvc.perform(post("/producoes/simular-alertas").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    /** #775 — todo 405 diz no cabeçalho Allow quais métodos o endereço aceita. */
    @Test
    void metodoNaoPermitidoTrazOCabecalhoAllow() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("allow-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        String token = "Bearer " + jwtTokenProvider.generateToken(usuario);

        mockMvc.perform(post("/usuarios/me/senha").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow", "PUT"));
    }

    /** #781 — 401 da camada de segurança sai em JSON (formato do ErrorResponseDTO), não vazio e sem Content-Type. */
    @Test
    void semTokenOuComTokenInvalidoRetorna401EmJson() throws Exception {
        for (String cabecalho : new String[] {null, "Bearer token-invalido"}) {
            var requisicao = get("/orcamentos");
            if (cabecalho != null) {
                requisicao = requisicao.header("Authorization", cabecalho);
            }
            mockMvc.perform(requisicao)
                    .andExpect(status().isUnauthorized())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Não autorizado"));
        }
    }
}

