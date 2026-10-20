package com.penseprecifique.api.catalogo;

import tools.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * #1027 — duplicar catálogo é entrada hostil: nome novo acima de 255 caracteres é recusado (400, nunca 500) e, sem nome novo, o
 * nome padrão "... (cópia)" de um catálogo com 255 caracteres é encurtado para caber.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CatalogoDuplicarNomeLongoHttpIT {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;

    private String token() {
        Usuario u = usuarioRepository.save(Usuario.builder().email("dup-longo-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        return jwtTokenProvider.generateToken(u);
    }

    private MvcResult criar(String token, String nome) throws Exception {
        return mockMvc.perform(post("/catalogos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"" + nome + "\"}")).andReturn();
    }

    private MvcResult duplicar(String token, String id, String corpo) throws Exception {
        return mockMvc.perform(post("/catalogos/" + id + "/duplicar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private Map<?, ?> corpoDe(MvcResult r) throws Exception {
        return objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    @Test
    void nomeNovoComMaisDe255CaracteresRetorna400() throws Exception {
        String t = token();
        String id = (String) corpoDe(criar(t, "Natal")).get("id");

        MvcResult r = duplicar(t, id, "{\"novoNome\":\"" + "x".repeat(256) + "\"}");

        assertEquals(400, r.getResponse().getStatus());
        assertTrue(((String) corpoDe(r).get("message")).contains("255"));
    }

    @Test
    void nomePadraoDeCatalogoComNomeDe255CaracteresEEncurtadoParaCaber() throws Exception {
        String t = token();
        MvcResult criado = criar(t, "n".repeat(255));
        assertEquals(201, criado.getResponse().getStatus());

        MvcResult r = duplicar(t, (String) corpoDe(criado).get("id"), "{}");

        assertEquals(201, r.getResponse().getStatus());
        String nome = (String) corpoDe(r).get("nome");
        assertEquals(255, nome.length());
        assertTrue(nome.endsWith(" (cópia)"));
    }
}
