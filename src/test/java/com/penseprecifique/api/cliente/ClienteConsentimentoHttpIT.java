package com.penseprecifique.api.cliente;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClienteConsentimentoHttpIT {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private Usuario usuario() {
        return usuarioRepository.save(Usuario.builder().email("consentimento-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
    }

    private String token(Usuario u) { return jwtTokenProvider.generateToken(u); }

    private UUID criarCliente(String token, String telefone) throws Exception {
        String body = "{\"nome\":\"Cliente consentimento\",\"ehCliente\":true,\"whatsapp\":\"" + telefone + "\"}";
        String resposta = mockMvc.perform(post("/clientes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(resposta, "$.id"));
    }

    @Test
    void recusaEReaceitePropagamPeloNumeroCanonicoEMantemHistorico() throws Exception {
        Usuario u = usuario();
        String token = token(u);
        UUID recusado = criarCliente(token, "(11) 91234-5678");
        UUID duplicado = criarCliente(token, "+55 11 91234-5678");

        mockMvc.perform(post("/clientes/{id}/consentimento/recusa", recusado).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"2026-10-30\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/clientes/{id}/consentimento/aceite", duplicado).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"2026-10-31\",\"descricao\":\"Maria pediu para voltar a receber\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/clientes/{id}/consentimento/historico", recusado).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tipo").value("ACEITE"))
                .andExpect(jsonPath("$[1].tipo").value("RECUSA"));
        mockMvc.perform(get("/clientes/{id}", recusado).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aceitaWhatsapp").value(true))
                .andExpect(jsonPath("$.recusouWhatsapp").value(false));
    }

    @Test
    void entradaHostilEIsolamentoNuncaViraramErroInterno() throws Exception {
        Usuario u = usuario();
        String token = token(u);
        UUID id = criarCliente(token, "11912345678");
        mockMvc.perform(post("/clientes/{id}/consentimento/recusa", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"1000000000-01-01\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/clientes/{id}/consentimento/recusa", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());

        String tokenOutra = token(usuario());
        mockMvc.perform(post("/clientes/{id}/consentimento/recusa", id).header("Authorization", "Bearer " + tokenOutra)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"2026-10-31\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/clientes/{id}/consentimento/historico", UUID.randomUUID()).header("Authorization", "Bearer " + tokenOutra))
                .andExpect(status().isNotFound());
    }

    @Test
    void novoAceiteDepoisDaRecusaExigeDescricao() throws Exception {
        String token = token(usuario());
        UUID id = criarCliente(token, "11912345678");
        mockMvc.perform(post("/clientes/{id}/consentimento/recusa", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"2026-10-30\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/clientes/{id}/consentimento/aceite", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"2026-10-31\",\"descricao\":\"curto\"}"))
                .andExpect(status().isBadRequest());
    }
}
