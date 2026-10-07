package com.penseprecifique.api.shared.exception;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * #762 (achado do Schemathesis no gate de segurança da V0.16.0) — requisição malformada nunca vira 500.
 * Roda contra o Tomcat real (porta aleatória), porque multipart sem boundary e parâmetro de consulta com nome
 * vazio são recusados pelo próprio contêiner, o que o MockMvc não reproduz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequisicaoMalformadaIT {

    @LocalServerPort int porta;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String token;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void usuario() {
        Usuario u = usuarioRepository.save(Usuario.builder()
                .email("malformada-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        token = jwtTokenProvider.generateToken(u);
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String contentType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Authorization", "Bearer " + token)
                .method(metodo, HttpRequest.BodyPublishers.noBody());
        if (contentType != null) b.header("Content-Type", contentType);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void multipartSemBoundaryRetorna400() throws Exception {
        for (String rota : new String[] {"/empresa", "/unidades-medida", "/configuracoes/precificacao"}) {
            HttpResponse<String> r = enviar("GET", rota, "multipart/form-data");
            assertEquals(400, r.statusCode(), rota + " -> " + r.body());
            assertFalse(r.body().contains("Erro interno"), r.body());
        }
        assertEquals(400, enviar("DELETE", "/empresa/logo", "multipart/form-data").statusCode());
    }

    @Test
    void parametroDeConsultaComNomeVazioRetorna400() throws Exception {
        for (String rota : new String[] {"/caixa/busca-itens-catalogo?=null", "/listas-compra?=85"}) {
            HttpResponse<String> r = enviar("GET", rota, null);
            assertEquals(400, r.statusCode(), rota + " -> " + r.body());
        }
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        HttpResponse<String> r = enviar("PUT", "/usuarios/me", null);
        assertEquals(405, r.statusCode(), r.body());
    }

    @Test
    void contentTypeNaoSuportadoRetorna415() throws Exception {
        HttpResponse<String> r = enviar("POST", "/auth/login", "text/plain");
        assertEquals(415, r.statusCode(), r.body());
    }
}
