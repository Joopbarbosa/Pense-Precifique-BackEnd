package com.penseprecifique.api.orcamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V0.18.0 (#1038, RN-NOVA-22) — comentários do orçamento pela API: 1 a 500 caracteres (CEN-NOVO-64), em qualquer status
 * (CEN-NOVO-65), conversa única em ordem cronológica (CEN-NOVO-63), da conta dona, e entrada hostil sem 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoComentariosHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "coment-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String orcamento(String t) throws Exception {
        MvcResult c = mvc.perform(post("/clientes").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"ehCliente\":true,\"ehFornecedor\":false}")).andReturn();
        String clienteId = json.readTree(c.getResponse().getContentAsString()).get("id").asText();
        MvcResult p = mvc.perform(post("/produtos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Caderno\",\"tipo\":\"PRODUTO\",\"tempoProducao\":10,\"rendimento\":1,\"precoVenda\":10,\"fichaTecnica\":[],\"estoqueAtual\":10}")).andReturn();
        String produtoId = json.readTree(p.getResponse().getContentAsString()).get("id").asText();
        MvcResult o = mvc.perform(post("/orcamentos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"" + clienteId + "\",\"metodoPagamento\":\"PIX\",\"temPrazoProducao\":false,\"itens\":[{\"produtoId\":\""
                        + produtoId + "\",\"quantidade\":1,\"precoUnitario\":10,\"margemAplicada\":0}]}")).andReturn();
        assertEquals(201, o.getResponse().getStatus(), o.getResponse().getContentAsString());
        return json.readTree(o.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult comentar(String t, String id, String texto) throws Exception {
        return mvc.perform(post("/orcamentos/" + id + "/comentarios").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":" + json.writeValueAsString(texto) + "}")).andReturn();
    }

    private JsonNode listar(String t, String id, String query) throws Exception {
        MvcResult r = mvc.perform(get("/orcamentos/" + id + "/comentarios" + query).header("Authorization", "Bearer " + t)).andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void cen63ComentarioApareceNoFimDaConversaComDataEHora() throws Exception {
        String t = token();
        String id = orcamento(t);
        assertEquals(201, comentar(t, id, "Cliente pediu entrega no sábado").getResponse().getStatus());
        assertEquals(201, comentar(t, id, "Combinei às 10h").getResponse().getStatus());
        JsonNode lista = listar(t, id, "");
        assertEquals(2, lista.get("totalElements").asInt());
        assertEquals("Cliente pediu entrega no sábado", lista.get("content").get(0).get("texto").asText());
        assertEquals("Combinei às 10h", lista.get("content").get(1).get("texto").asText());
        assertTrue(lista.get("content").get(1).get("criadoEm").asText().length() > 10);
    }

    @Test
    void cen64VazioSoEspacosEAcimaDe500Bloqueiam_500Passa() throws Exception {
        String t = token();
        String id = orcamento(t);
        assertEquals(400, comentar(t, id, "").getResponse().getStatus());
        assertEquals(400, comentar(t, id, "     ").getResponse().getStatus());
        assertEquals(400, comentar(t, id, "a".repeat(501)).getResponse().getStatus());
        assertEquals(201, comentar(t, id, "a".repeat(500)).getResponse().getStatus());
        // espaços das pontas não contam: 500 letras com espaços em volta passa e é gravado aparado
        MvcResult aparado = comentar(t, id, "  " + "b".repeat(500) + "  ");
        assertEquals(201, aparado.getResponse().getStatus());
        assertEquals("b".repeat(500), json.readTree(aparado.getResponse().getContentAsString()).get("texto").asText());
    }

    @Test
    void cen65ComentaEmOrcamentoCanceladoENaoHaEdicaoNemExclusao() throws Exception {
        String t = token();
        String id = orcamento(t);
        mvc.perform(post("/orcamentos/" + id + "/cancelar").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertEquals(201, comentar(t, id, "Cliente desistiu").getResponse().getStatus());
        String idComentario = listar(t, id, "").get("content").get(0).get("id").asText();
        int put = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/orcamentos/" + id + "/comentarios/" + idComentario)
                .header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"x\"}")).andReturn().getResponse().getStatus();
        int del = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/orcamentos/" + id + "/comentarios/" + idComentario)
                .header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus();
        assertTrue(put == 404 || put == 405, "editar não existe: " + put);
        assertTrue(del == 404 || del == 405, "excluir não existe: " + del);
    }

    /** Paginação: página 0 = os mais recentes, cada página em ordem cronológica (RN-086). */
    @Test
    void paginaZeroTrazOsMaisRecentesEmOrdemCronologica() throws Exception {
        String t = token();
        String id = orcamento(t);
        for (int i = 1; i <= 25; i++) comentar(t, id, "c" + i);
        JsonNode p0 = listar(t, id, "?size=20");
        assertEquals(25, p0.get("totalElements").asInt());
        assertEquals(20, p0.get("content").size());
        assertEquals("c6", p0.get("content").get(0).get("texto").asText());
        assertEquals("c25", p0.get("content").get(19).get("texto").asText());
        JsonNode p1 = listar(t, id, "?size=20&page=1");
        assertEquals(5, p1.get("content").size());
        assertEquals("c1", p1.get("content").get(0).get("texto").asText());
    }

    /** Orçamento de outra conta: 404, sem revelar; entrada hostil nunca vira 500. */
    @Test
    void outraContaEEntradaHostil() throws Exception {
        String dono = token();
        String intruso = token();
        String id = orcamento(dono);
        assertEquals(404, comentar(intruso, id, "oi").getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/orcamentos/" + id + "/comentarios").header("Authorization", "Bearer " + intruso)).andReturn().getResponse().getStatus());
        assertEquals(404, comentar(dono, UUID.randomUUID().toString(), "oi").getResponse().getStatus());
        String[] hostis = {"/orcamentos/nao-e-uuid/comentarios", "/orcamentos/" + id + "/comentarios?page=-1", "/orcamentos/" + id + "/comentarios?size=0",
                "/orcamentos/" + id + "/comentarios?size=999999", "/orcamentos/" + id + "/comentarios?page=abc"};
        for (String url : hostis) {
            int s = mvc.perform(get(url).header("Authorization", "Bearer " + dono)).andReturn().getResponse().getStatus();
            assertTrue(s < 500, url + " -> " + s);
        }
        int semCorpo = mvc.perform(post("/orcamentos/" + id + "/comentarios").header("Authorization", "Bearer " + dono)
                .contentType(MediaType.APPLICATION_JSON).content("{")).andReturn().getResponse().getStatus();
        int tipoErrado = mvc.perform(post("/orcamentos/" + id + "/comentarios").header("Authorization", "Bearer " + dono)
                .contentType(MediaType.TEXT_PLAIN).content("texto")).andReturn().getResponse().getStatus();
        int nulo = comentar(dono, id, "ab\u0000cd").getResponse().getStatus();
        assertEquals(400, semCorpo);
        assertTrue(tipoErrado == 415 || tipoErrado == 400, "content-type: " + tipoErrado);
        assertEquals(400, nulo);
    }
}
