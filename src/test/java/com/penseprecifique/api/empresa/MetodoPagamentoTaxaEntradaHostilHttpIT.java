package com.penseprecifique.api.empresa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.pdf.PdfMicroservicoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** PP-702 — entradas hostis em taxas uniformes e por parcela devem ser erros 400, nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MetodoPagamentoTaxaEntradaHostilHttpIT {

    @Autowired MockMvc mvc;
    @MockitoBean PdfMicroservicoClient pdfMicroservicoClient;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void taxaMaquininhaRecusaPercentualNegativoETextoCom400() throws Exception {
        String token = criarConta();
        UUID id = metodo(token, TipoMetodoPagamento.CARTAO_DEBITO);
        for (String taxa : new String[]{"500", "-1", "\"abc\""}) {
            int status = atualizar(token, id, "{\"taxaMaquininha\":" + taxa + "}");
            assertEquals(400, status, "taxaMaquininha=" + taxa);
        }
    }

    @Test
    void taxaPorParcelaRecusaPercentualNegativoTextoENuloCom400() throws Exception {
        String token = criarConta();
        UUID id = metodo(token, TipoMetodoPagamento.CARTAO_CREDITO);
        for (String taxa : new String[]{"500", "-1", "\"abc\"", "null"}) {
            String corpo = "{\"maxParcelas\":1,\"taxaParcelaUniforme\":false,"
                    + "\"taxasParcela\":[{\"parcela\":1,\"taxa\":" + taxa + "}]}";
            assertEquals(400, atualizar(token, id, corpo), "taxa por parcela=" + taxa);
        }
    }

    @Test
    void taxaUniformeEPorParcelaAceitam100PorCento() throws Exception {
        String token = criarConta();
        UUID debito = metodo(token, TipoMetodoPagamento.CARTAO_DEBITO);
        UUID credito = metodo(token, TipoMetodoPagamento.CARTAO_CREDITO);
        assertEquals(200, atualizar(token, debito, "{\"taxaMaquininha\":100}"));
        assertEquals(200, atualizar(token, credito, "{\"maxParcelas\":1,\"taxaParcelaUniforme\":false,"
                + "\"taxasParcela\":[{\"parcela\":1,\"taxa\":100}]}"));
    }

    private String criarConta() throws Exception {
        String email = "taxa-hostil-" + UUID.randomUUID() + "@test.com";
        String senha = "senha-valida-123";
        var resposta = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha
                        + "\",\"confirmarSenha\":\"" + senha + "\",\"nomeEmpresa\":\"Teste\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("token").asText();
    }

    private UUID metodo(String token, TipoMetodoPagamento tipo) throws Exception {
        JsonNode metodos = json.readTree(mvc.perform(get("/configuracoes/metodos-pagamento")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        for (JsonNode metodo : metodos) {
            if (tipo.name().equals(metodo.path("tipo").asText())) return UUID.fromString(metodo.path("id").asText());
        }
        throw new AssertionError("Método não encontrado: " + tipo);
    }

    private int atualizar(String token, UUID id, String corpo) throws Exception {
        return mvc.perform(put("/configuracoes/metodos-pagamento/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getStatus();
    }
}
