package com.penseprecifique.api.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** V0.19.0 (#1172, CEN-NOVO-55) — rotas do financeiro e entrada hostil: corpo malformado, content-type errado, datas e números extremos, id de outra conta; nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FinanceiroHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired LancamentoFinanceiroService service;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoRepository;
    @Autowired Clock relogioBrasilia;
    private final ObjectMapper json = new ObjectMapper();

    private record Conta(String email, String token, Usuario usuario) {}

    private Conta conta() throws Exception {
        String email = "fin-http-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        return new Conta(email, token, usuarioRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow());
    }

    private UUID metodo(Conta c) {
        return metodoRepository.save(MetodoPagamentoConfiguravel.builder().usuario(c.usuario()).tipo(TipoMetodoPagamento.OUTRO)
                .nome("Boleto " + UUID.randomUUID()).ativo(true).ordem(1).build()).getId();
    }

    private UUID parcela(Conta c, String valor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(c.email(), null, List.of()));
        Lancamento l = service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "Aluguel", OrigemLancamento.AVULSO, null, null,
                DivisaoParcelas.dividir(new BigDecimal(valor), 1, LocalDate.now(relogioBrasilia).minusDays(3))));
        return parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId()).get(0).getId();
    }

    private MvcResult baixa(Conta c, Object id, String corpo) throws Exception {
        return mvc.perform(post("/financeiro/parcelas/" + id + "/baixa").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    @Test
    void listaBaixaEDesfazPeloHttp() throws Exception {
        Conta c = conta();
        UUID m = metodo(c);
        UUID id = parcela(c, "45.90");
        MvcResult lista = mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR&situacao=VENCIDAS&de=2000-01-01&ate=2100-12-31")
                .header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, status(lista), lista.getResponse().getContentAsString());
        JsonNode corpo = json.readTree(lista.getResponse().getContentAsString());
        assertEquals(1, corpo.get("itens").size());
        assertEquals("Vencida há 3 dias", corpo.get("itens").get(0).get("rotulo").asText());
        assertEquals(45.90, corpo.get("totalVencido").asDouble(), 0.001);
        MvcResult ok = baixa(c, id, "{\"metodoPagamentoId\":\"" + m + "\"}");
        assertEquals(200, status(ok), ok.getResponse().getContentAsString());
        JsonNode b = json.readTree(ok.getResponse().getContentAsString());
        assertEquals("PAGA", b.get("situacaoExibida").asText());
        assertEquals(LocalDate.now(relogioBrasilia).toString(), b.get("dataBaixa").asText());
        assertEquals(400, status(baixa(c, id, "{\"metodoPagamentoId\":\"" + m + "\"}")));
        MvcResult des = mvc.perform(post("/financeiro/parcelas/" + id + "/desfazer-baixa").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, status(des));
        assertEquals("VENCIDA", json.readTree(des.getResponse().getContentAsString()).get("situacaoExibida").asText());
        assertEquals(400, status(mvc.perform(post("/financeiro/parcelas/" + id + "/desfazer-baixa").header("Authorization", "Bearer " + c.token())).andReturn()));
    }

    /** PP-697 — sem período, a lista traz a vencida de mês anterior e o cartão Vencido a soma, com 2 casas; outra conta não vê. */
    @Test
    void padraoIncluiVencidaDeMesAnteriorPeloHttp() throws Exception {
        Conta c = conta();
        Conta outra = conta();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(c.email(), null, List.of()));
        service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "Boleto", OrigemLancamento.AVULSO, null, null,
                DivisaoParcelas.dividir(new BigDecimal("500.00"), 1, LocalDate.now(relogioBrasilia).minusDays(45))));
        MvcResult r = mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, status(r), r.getResponse().getContentAsString());
        JsonNode corpo = json.readTree(r.getResponse().getContentAsString());
        assertEquals(1, corpo.get("itens").size());
        assertEquals("VENCIDA", corpo.get("itens").get(0).get("situacaoExibida").asText());
        assertEquals(new BigDecimal("500.00"), corpo.get("totalVencido").decimalValue().setScale(2));
        assertEquals(new BigDecimal("500.00"), corpo.get("totalEmAberto").decimalValue().setScale(2));
        MvcResult explicito = mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR&de=2000-01-01&ate=2000-01-31").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(0, json.readTree(explicito.getResponse().getContentAsString()).get("itens").size());
        MvcResult alheia = mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR").header("Authorization", "Bearer " + outra.token())).andReturn();
        assertEquals(0, json.readTree(alheia.getResponse().getContentAsString()).get("itens").size());
    }

    @Test
    void semTokenNaoEntra() throws Exception {
        int s = status(mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR")).andReturn());
        assertTrue(s == 401 || s == 403, "sem token: " + s);
    }

    @Test
    void baixaHostil() throws Exception {
        Conta c = conta();
        UUID m = metodo(c);
        UUID id = parcela(c, "10.00");
        String[] corpos = {
                "{}", "{\"metodoPagamentoId\":null}", "{\"metodoPagamentoId\":\"nao-uuid\"}", "{\"metodoPagamentoId\":123}",
                "{\"metodoPagamentoId\":\"" + m + "\",\"dataBaixa\":\"abc\"}", "{\"metodoPagamentoId\":\"" + m + "\",\"dataBaixa\":\"2999-01-01\"}",
                "{\"metodoPagamentoId\":\"" + m + "\",\"dataBaixa\":\"1800-01-01\"}", "{\"metodoPagamentoId\":\"" + m + "\",\"dataBaixa\":\"-999999999-01-01\"}",
                "{\"metodoPagamentoId\":\"" + m + "\",\"dataBaixa\":99999999999}", "{\"metodoPagamentoId\":\"" + m + "\",\"dataBaixa\":\"2030-02-31\"}",
                "{\"metodoPagamentoId\":\"" + UUID.randomUUID() + "\"}", "{\"metodoPagamentoId\":\"" + "a".repeat(100000) + "\"}",
                "{\"metodoPagamentoId\":\"\\u0000\"}", "{", "[]", "null", "\"texto\"", "",
        };
        for (String corpo : corpos) {
            int s = status(baixa(c, id, corpo));
            assertTrue(s == 400 || s == 404, corpo.substring(0, Math.min(80, corpo.length())) + " -> " + s);
        }
        int tipoErrado = status(mvc.perform(post("/financeiro/parcelas/" + id + "/baixa").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.TEXT_PLAIN).content("x")).andReturn());
        assertTrue(tipoErrado == 415 || tipoErrado == 400, "content-type: " + tipoErrado);
        int semTipo = status(mvc.perform(post("/financeiro/parcelas/" + id + "/baixa").header("Authorization", "Bearer " + c.token())).andReturn());
        assertTrue(semTipo == 415 || semTipo == 400, "sem content-type: " + semTipo);
    }

    @Test
    void idsHostis() throws Exception {
        Conta dono = conta();
        Conta intruso = conta();
        UUID id = parcela(dono, "10.00");
        UUID mIntruso = metodo(intruso);
        UUID mDono = metodo(dono);
        String corpo = "{\"metodoPagamentoId\":\"" + mIntruso + "\"}";
        assertEquals(404, status(baixa(intruso, id, corpo)));
        assertEquals(404, status(baixa(dono, id, corpo)));
        assertEquals(404, status(baixa(intruso, id, "{\"metodoPagamentoId\":\"" + mDono + "\"}")));
        assertEquals(404, status(baixa(dono, UUID.randomUUID(), "{\"metodoPagamentoId\":\"" + mDono + "\"}")));
        assertEquals(404, status(mvc.perform(post("/financeiro/parcelas/" + id + "/desfazer-baixa").header("Authorization", "Bearer " + intruso.token())).andReturn()));
        assertEquals(404, status(mvc.perform(get("/financeiro/parcelas/" + id).header("Authorization", "Bearer " + intruso.token())).andReturn()));
        for (String ruim : new String[] {"nao-uuid", "123", "%00", "-1", "a".repeat(5000)}) {
            int s = status(baixa(dono, ruim, "{\"metodoPagamentoId\":\"" + mDono + "\"}"));
            assertTrue(s == 400 || s == 404, ruim.substring(0, Math.min(20, ruim.length())) + " -> " + s);
            int d = status(mvc.perform(post("/financeiro/parcelas/" + ruim + "/desfazer-baixa").header("Authorization", "Bearer " + dono.token())).andReturn());
            assertTrue(d == 400 || d == 404, "desfazer " + d);
            int g = status(mvc.perform(get("/financeiro/parcelas/" + ruim).header("Authorization", "Bearer " + dono.token())).andReturn());
            assertTrue(g == 400 || g == 404, "buscar " + g);
        }
        // nada mudou na parcela do dono
        assertEquals("VENCIDA", json.readTree(mvc.perform(get("/financeiro/parcelas/" + id).header("Authorization", "Bearer " + dono.token()))
                .andReturn().getResponse().getContentAsString()).get("situacaoExibida").asText());
    }

    @Test
    void listaHostil() throws Exception {
        Conta c = conta();
        String[] consultas = {
                "", "?tipo=", "?tipo=XYZ", "?tipo=a_pagar", "?tipo=A_PAGAR&situacao=XYZ", "?tipo=A_PAGAR&de=abc", "?tipo=A_PAGAR&ate=2030-13-45",
                "?tipo=A_PAGAR&de=1800-01-01", "?tipo=A_PAGAR&ate=9999-12-31", "?tipo=A_PAGAR&de=2030-05-10&ate=2030-05-01",
                "?tipo=A_PAGAR&de=-999999999-01-01", "?tipo=A_PAGAR&page=-1", "?tipo=A_PAGAR&size=0", "?tipo=A_PAGAR&size=100000",
                "?tipo=A_PAGAR&page=abc", "?tipo=A_PAGAR&page=99999999999", "?tipo=A_PAGAR&sort=inexistente,asc", "?tipo=A_PAGAR&sort=valor;drop table x",
                "?tipo=A_PAGAR&tipo=A_RECEBER", "?tipo=A_PAGAR&situacao=" + "a".repeat(50000),
        };
        for (String q : consultas) {
            int s = status(mvc.perform(get("/financeiro/parcelas" + q).header("Authorization", "Bearer " + c.token())).andReturn());
            assertTrue(s >= 200 && s < 500, q.substring(0, Math.min(60, q.length())) + " -> " + s);
        }
        assertEquals(400, status(mvc.perform(get("/financeiro/parcelas?tipo=XYZ").header("Authorization", "Bearer " + c.token())).andReturn()));
        assertEquals(400, status(mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR&de=2030-05-10&ate=2030-05-01").header("Authorization", "Bearer " + c.token())).andReturn()));
        assertEquals(400, status(mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR&de=1800-01-01").header("Authorization", "Bearer " + c.token())).andReturn()));
    }

    @Test
    void contaAvulsaHttpRecusaEntradaHostilEIsolaIds() throws Exception {
        Conta a = conta();
        Conta b = conta();
        String base = "\"tipo\":\"A_PAGAR\",\"descricao\":\"Aluguel\",\"valor\":50.25,\"vencimento\":\"2027-01-31\"";
        MvcResult criada = mvc.perform(post("/financeiro/lancamentos-avulsos").header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON).content("{" + base + ",\"meses\":3,\"foraProjecao\":true}" )).andReturn();
        assertEquals(201, status(criada), criada.getResponse().getContentAsString());
        JsonNode lista = json.readTree(mvc.perform(get("/financeiro/parcelas?tipo=A_PAGAR&de=2027-01-01&ate=2027-04-30")
                .header("Authorization", "Bearer " + a.token())).andReturn().getResponse().getContentAsString());
        assertEquals(3, lista.get("itens").size());
        assertTrue(java.util.stream.StreamSupport.stream(lista.get("itens").spliterator(), false)
                .allMatch(item -> item.get("foraProjecao").asBoolean()));
        UUID id = UUID.fromString(lista.get("itens").get(0).get("id").asText());
        UUID lancamentoId = UUID.fromString(lista.get("itens").get(0).get("lancamentoId").asText());
        MvcResult previsaoPropria = mvc.perform(get("/financeiro/parcelas/" + id + "/avulso/impacto-exclusao")
                .header("Authorization", "Bearer " + a.token())).andReturn();
        assertEquals(200, status(previsaoPropria));
        assertEquals(false, json.readTree(previsaoPropria.getResponse().getContentAsString()).get("excluiContaInteira").asBoolean());
        for (String corpo : List.of("{" + base.replace("Aluguel", "x") + "}", "{" + base + ",\"meses\":0}",
                "{" + base + ",\"meses\":1}", "{" + base + ",\"meses\":25}",
                "{" + base.replace("50.25", "0") + "}", "{" + base.replace("50.25", "100000000") + "}",
                "{" + base.replace("50.25", "12.345") + "}", "{" + base.replace("2027-01-31", "1999-01-01") + "}",
                "{" + base.replace("2027-01-31", "2101-01-01") + "}",
                "{" + base.replace("2027-01-31", "31/02/2027") + "}",
                "{" + base.replace("50.25", "\"\\u0000\"") + "}",
                "{" + base.replace("Aluguel", "a".repeat(81)) + "}", "{" + base.replace("Aluguel", "a".repeat(100000)) + "}",
                "{" + base.replace("Aluguel", "\\u0000") + "}", "{", "[]", "null", "\"texto\"", "")) {
            int s = status(mvc.perform(post("/financeiro/lancamentos-avulsos").header("Authorization", "Bearer " + a.token())
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn());
            assertTrue(s == 400 || s == 404, "corpo inválido respondeu " + s);
        }
        int errado = status(mvc.perform(post("/financeiro/lancamentos-avulsos").header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.TEXT_PLAIN).content("x")).andReturn());
        assertEquals(415, errado);
        String authA = "Bearer " + a.token();
        for (String corpo : List.of("{", "{\"descricao\":\"" + "x".repeat(81) + "\"}",
                "{\"descricao\":\"" + "x".repeat(501) + "\"}", "{\"descricao\":\"" + "x".repeat(100000) + "\"}",
                "{\"descricao\":\"\\u0000\"}", "{\"valor\":1e1000}")) {
            int s = status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/financeiro/parcelas/" + id + "/avulso")
                    .header("Authorization", authA).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn());
            assertEquals(400, s, "edição inválida não pode gerar erro interno");
        }
        assertEquals(400, status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/financeiro/parcelas/" + id + "/avulso")
                .header("Authorization", authA).contentType(MediaType.APPLICATION_JSON).content("{}" )).andReturn()));
        assertEquals(400, status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/financeiro/lancamentos/" + lancamentoId + "/fora-projecao")
                .header("Authorization", authA).contentType(MediaType.APPLICATION_JSON).content("{}" )).andReturn()));
        assertTrue(status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/financeiro/parcelas/" + id + "/avulso?seguintes=abc")
                .header("Authorization", authA)).andReturn()) < 500);
        int getAlheio = status(mvc.perform(get("/financeiro/parcelas/" + id).header("Authorization", "Bearer " + b.token())).andReturn());
        int getInexistente = status(mvc.perform(get("/financeiro/parcelas/" + UUID.randomUUID()).header("Authorization", "Bearer " + b.token())).andReturn());
        assertEquals(404, getAlheio); assertEquals(getInexistente, getAlheio);
        JsonNode respostaAlheia = json.readTree(mvc.perform(get("/financeiro/parcelas/" + id).header("Authorization", "Bearer " + b.token())).andReturn().getResponse().getContentAsString());
        JsonNode respostaInexistente = json.readTree(mvc.perform(get("/financeiro/parcelas/" + UUID.randomUUID()).header("Authorization", "Bearer " + b.token())).andReturn().getResponse().getContentAsString());
        ((ObjectNode) respostaAlheia).remove("timestamp");
        ((ObjectNode) respostaInexistente).remove("timestamp");
        assertEquals(respostaInexistente, respostaAlheia);
        String authB = "Bearer " + b.token();
        String edicao = "{\"descricao\":\"Conta editada\",\"valor\":51.00}";
        assertEquals(404, status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/financeiro/parcelas/" + id + "/avulso")
                .header("Authorization", authB).contentType(MediaType.APPLICATION_JSON).content(edicao)).andReturn()));
        assertEquals(404, status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/financeiro/parcelas/" + id + "/avulso")
                .header("Authorization", authB)).andReturn()));
        assertEquals(404, status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/financeiro/lancamentos/" + lancamentoId + "/fora-projecao")
                .header("Authorization", authB).contentType(MediaType.APPLICATION_JSON).content("{\"foraProjecao\":false}")).andReturn()));
        assertEquals(404, status(mvc.perform(get("/financeiro/parcelas/" + id + "/avulso/impacto-exclusao")
                .header("Authorization", authB)).andReturn()));
        for (String idRuim : List.of("nao-uuid", "123", "%00", "a".repeat(5000))) {
            int s = status(mvc.perform(get("/financeiro/parcelas/" + idRuim + "/avulso/impacto-exclusao")
                    .header("Authorization", authA)).andReturn());
            assertTrue(s == 400 || s == 404, "prévia de exclusão com id inválido: " + s);
        }
    }
}
