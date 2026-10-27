package com.penseprecifique.api.agenda;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** V0.18.0 (#1039) — rotas do compromisso da usuária e entrada hostil: corpo malformado, tipo errado, datas extremas, id de outra conta; nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CompromissosUsuariaHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "agenda-http-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private MvcResult criar(String t, String corpo) throws Exception {
        return mvc.perform(post("/agenda/compromissos-usuaria").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    @Test
    void criaConsultaConcluiECancela() throws Exception {
        String t = token();
        MvcResult r = criar(t, "{\"titulo\":\"Comprar linha\",\"data\":\"2030-05-10\",\"horario\":\"10:00\"}");
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode d = json.readTree(r.getResponse().getContentAsString());
        String id = d.get("id").asText();
        assertEquals("2030-05-10", d.get("dataOriginal").asText());
        MvcResult lista = mvc.perform(get("/agenda/compromissos?de=2030-05-06&ate=2030-05-12").header("Authorization", "Bearer " + t)).andReturn();
        JsonNode itens = json.readTree(lista.getResponse().getContentAsString());
        assertEquals(1, itens.size());
        assertEquals("2030-05-10", itens.get(0).get("dataOriginalOcorrencia").asText());
        assertEquals("10:00", itens.get(0).get("horario").asText());
        assertEquals(200, mvc.perform(post("/agenda/compromissos-usuaria/" + id + "/ocorrencias/2030-05-10/concluir").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post("/agenda/compromissos-usuaria/" + id + "/ocorrencias/2030-05-10/concluir").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus());
    }

    /** CEN-NOVO-52 e entrada hostil na criação. */
    @Test
    void criacaoHostil() throws Exception {
        String t = token();
        String[] corpos = {
                "{}", "{\"titulo\":\"\",\"data\":\"2030-05-10\"}", "{\"titulo\":\"   \",\"data\":\"2030-05-10\"}", "{\"titulo\":\"Reunião\"}",
                "{\"titulo\":\"" + "a".repeat(121) + "\",\"data\":\"2030-05-10\"}", "{\"titulo\":\"ab\\u0000cd\",\"data\":\"2030-05-10\"}",
                "{\"titulo\":\"x\",\"data\":\"abc\"}", "{\"titulo\":\"x\",\"data\":\"1999-12-31\"}", "{\"titulo\":\"x\",\"data\":\"2101-01-01\"}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"horario\":\"25:99\"}", "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"recorrencia\":\"DIARIA\"}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"recorrencia\":\"SEMANAL\"}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"recorrencia\":\"SEMANAL\",\"diasSemana\":[null],\"quantidadeOcorrencias\":3}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"recorrencia\":\"SEMANAL\",\"diasSemana\":[\"MONDAY\"],\"quantidadeOcorrencias\":1}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"recorrencia\":\"SEMANAL\",\"diasSemana\":[\"MONDAY\"],\"quantidadeOcorrencias\":366}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"recorrencia\":\"SEMANAL\",\"diasSemana\":[\"MONDAY\"],\"quantidadeOcorrencias\":-5}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"quantidadeOcorrencias\":99999999999}", "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"orcamentoId\":\"nao-uuid\"}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"descricao\":\"" + "d".repeat(501) + "\"}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"descricao\":\"ab\\u0000cd\"}",
                "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"descricao\":123}",
                "{", "[]", "null",
        };
        for (String corpo : corpos) {
            int s = criar(t, corpo).getResponse().getStatus();
            assertEquals(400, s, corpo + " -> " + s);
        }
        int tipoErrado = mvc.perform(post("/agenda/compromissos-usuaria").header("Authorization", "Bearer " + t)
                .contentType(MediaType.TEXT_PLAIN).content("titulo")).andReturn().getResponse().getStatus();
        assertTrue(tipoErrado == 415 || tipoErrado == 400, "content-type: " + tipoErrado);
        assertEquals(404, criar(t, "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"insumoId\":\"" + UUID.randomUUID() + "\"}").getResponse().getStatus());
        assertEquals(404, criar(t, "{\"titulo\":\"x\",\"data\":\"2030-05-10\",\"orcamentoId\":\"" + UUID.randomUUID() + "\"}").getResponse().getStatus());
    }

    /** CEN-NOVO-132 — descrição opcional de até 500 caracteres: aparada, vazia vira "sem descrição"; 501 é recusada. */
    @Test
    void descricaoOpcionalAte500() throws Exception {
        String t = token();
        MvcResult r = criar(t, "{\"titulo\":\"Ligar\",\"data\":\"2030-05-10\",\"descricao\":\"  Falar do prazo  \"}");
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("Falar do prazo", json.readTree(r.getResponse().getContentAsString()).get("descricao").asText());
        MvcResult cheia = criar(t, "{\"titulo\":\"Cheia\",\"data\":\"2030-05-10\",\"descricao\":\"" + "d".repeat(500) + "\"}");
        assertEquals(201, cheia.getResponse().getStatus());
        MvcResult vazia = criar(t, "{\"titulo\":\"Vazia\",\"data\":\"2030-05-10\",\"descricao\":\"   \"}");
        assertEquals(201, vazia.getResponse().getStatus());
        assertTrue(json.readTree(vazia.getResponse().getContentAsString()).get("descricao").isNull());
    }

    /** Ações hostis: data malformada, ocorrência inexistente, id de outra conta, observação curta, corpo malformado. */
    @Test
    void acoesHostis() throws Exception {
        String dono = token();
        String intruso = token();
        String id = json.readTree(criar(dono, "{\"titulo\":\"Meu\",\"data\":\"2030-05-10\"}").getResponse().getContentAsString()).get("id").asText();
        String base = "/agenda/compromissos-usuaria/" + id + "/ocorrencias/";
        assertEquals(404, mvc.perform(get(base + "2030-05-10").header("Authorization", "Bearer " + intruso)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post(base + "2030-05-10/concluir").header("Authorization", "Bearer " + intruso)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get(base + "2030-05-11").header("Authorization", "Bearer " + dono)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/agenda/compromissos-usuaria/" + UUID.randomUUID() + "/ocorrencias/2030-05-10").header("Authorization", "Bearer " + dono)).andReturn().getResponse().getStatus());
        for (String url : new String[] {base + "nao-e-data", base + "2030-13-45", base + "-999999999-01-01", "/agenda/compromissos-usuaria/nao-uuid/ocorrencias/2030-05-10"}) {
            int s = mvc.perform(get(url).header("Authorization", "Bearer " + dono)).andReturn().getResponse().getStatus();
            assertTrue(s == 400 || s == 404, url + " -> " + s);
        }
        for (String obs : new String[] {"", "curta", "x".repeat(501)}) {
            int s = mvc.perform(post(base + "2030-05-10/cancelar").header("Authorization", "Bearer " + dono)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\":" + json.writeValueAsString(obs) + "}")).andReturn().getResponse().getStatus();
            assertEquals(400, s, "observação: " + obs.length());
        }
        assertEquals(400, mvc.perform(post(base + "2030-05-10/cancelar").header("Authorization", "Bearer " + dono)
                .contentType(MediaType.APPLICATION_JSON).content("{")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(put(base + "2030-05-10").header("Authorization", "Bearer " + dono)
                .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"\"}")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post(base + "2030-05-10/cancelar").header("Authorization", "Bearer " + dono)
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\":\"" + "o".repeat(30) + "\"}")).andReturn().getResponse().getStatus());
    }
}
