package com.penseprecifique.api.caixa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaItemCustomizacaoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaItemRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** PP-672 — RN-NOVA-49, CEN-NOVO-72 a 75: limites da quantidade protegidos no endpoint. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VendaCaixaQuantidadeHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired Validator validator;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "venda-quantidade-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA
                                + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private MvcResult enviar(String token, String quantidadeItem, String quantidadeCustomizacao) throws Exception {
        String customizacao = quantidadeCustomizacao == null ? "[]"
                : "[{\"produtoId\":\"" + UUID.randomUUID() + "\",\"quantidade\":" + quantidadeCustomizacao + "}]";
        String body = "{\"itens\":[{\"produtoId\":\"" + UUID.randomUUID() + "\",\"quantidade\":"
                + quantidadeItem + ",\"customizacoes\":" + customizacao
                + "}],\"pagamentos\":[{\"metodoPagamentoId\":\"" + UUID.randomUUID() + "\",\"valor\":1}]}";
        return mvc.perform(post("/caixa/vendas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    @Test
    void entradasHostisDoItemSao400SemErroInterno() throws Exception {
        String token = token();
        for (String quantidade : new String[]{"1000000", "1e20", "1e999", "-1", "0", "0.001", "1.001", "\"abc\"", "null", "\"\\u0000\""}) {
            MvcResult resultado = enviar(token, quantidade, null);
            assertEquals(400, resultado.getResponse().getStatus(), "quantidade do item: " + quantidade);
            assertTrue(resultado.getResponse().getContentAsString().length() > 0);
            assertTrue(!resultado.getResponse().getContentAsString().contains("Exception"));
        }
    }

    @Test
    void entradasHostisDaCustomizacaoSao400SemErroInterno() throws Exception {
        String token = token();
        for (String quantidade : new String[]{"1000000", "1e20", "1e999", "-1", "0", "1.5", "\"abc\"", "null", "\"\\u0000\""}) {
            MvcResult resultado = enviar(token, "1", quantidade);
            assertEquals(400, resultado.getResponse().getStatus(), "quantidade da customização: " + quantidade);
            assertTrue(!resultado.getResponse().getContentAsString().contains("Exception"));
        }
    }

    @Test
    void corpoJsonMalformadoNoEndpointRecebe400() throws Exception {
        MvcResult resultado = mvc.perform(post("/caixa/vendas").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andReturn();
        assertEquals(400, resultado.getResponse().getStatus());
        assertTrue(!resultado.getResponse().getContentAsString().contains("Exception"));
    }

    @Test
    void fronteirasPermitidasPassamPelaValidacaoDosDtos() {
        assertTrue(validator.validate(new VendaCaixaItemRequestDTO(null, UUID.randomUUID(),
                new BigDecimal("0.01"), null)).isEmpty());
        assertTrue(validator.validate(new VendaCaixaItemRequestDTO(null, UUID.randomUUID(),
                new BigDecimal("999999.99"), null)).isEmpty());
        assertTrue(validator.validate(new VendaCaixaItemCustomizacaoRequestDTO(UUID.randomUUID(), 999999)).isEmpty());
        assertEquals(1, validator.validate(new VendaCaixaItemRequestDTO(null, UUID.randomUUID(),
                new BigDecimal("1.001"), null)).size());
        assertEquals(1, validator.validate(new VendaCaixaItemCustomizacaoRequestDTO(UUID.randomUUID(), 1000000)).size());
    }

    @Test
    void valoresValidosNoTetoPassamPelaValidacaoHttp() throws Exception {
        String token = token();
        // O serviço pode bloquear por turno ou produto; a resposta não deve atribuir
        // o bloqueio à quantidade quando o DTO recebeu os limites válidos.
        assertTrue(!enviar(token, "999999.99", null).getResponse().getContentAsString().contains("quantidade"));
        assertTrue(!enviar(token, "1", "999999").getResponse().getContentAsString().contains("quantidade"));
    }
}
