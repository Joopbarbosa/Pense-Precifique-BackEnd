package com.penseprecifique.api.compra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** V0.19.0 (#1173) — vencimento e parcelas da compra pelo HTTP, com entrada hostil: nunca 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CompraFinanceiroHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoRepository;
    @Autowired Clock relogioBrasilia;
    private final ObjectMapper json = new ObjectMapper();

    private record Conta(String token, UUID insumo, UUID credito) {}

    private Conta conta() throws Exception {
        String email = "compra-http-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        Usuario u = usuarioRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        UnidadeMedida m = unidadeMedidaRepository.findByUsuarioIdAndDeletedAtIsNullOrderByNomeAsc(u.getId()).get(0);
        Insumo i = insumoRepository.save(Insumo.builder().usuario(u).numero(1).nome("Fita").unidadeMedida(m)
                .estoqueAtual(BigDecimal.TEN).custoUnitario(new BigDecimal("2.00")).permitirEstoqueNegativo(true).build());
        MetodoPagamentoConfiguravel c = metodoRepository.findByUsuarioIdOrderByOrdemAscTipoAsc(u.getId()).stream()
                .filter(x -> x.getTipo() == TipoMetodoPagamento.CARTAO_CREDITO).findFirst().orElseThrow();
        return new Conta(token, i.getId(), c.getId());
    }

    private String corpo(Conta c, String pago, String metodo, String parcelas, String vencimento) {
        return "{\"dataCompra\":\"" + LocalDate.now(relogioBrasilia) + "\",\"pago\":" + pago + ",\"metodoPagamentoId\":" + metodo
                + ",\"parcelas\":" + parcelas + ",\"vencimento\":" + vencimento + ",\"entregaImediata\":true,"
                + "\"itens\":[{\"insumoId\":\"" + c.insumo() + "\",\"quantidade\":1,\"precoTotal\":100.00}]}";
    }

    private MvcResult enviar(Conta c, String caminho, String corpo, MediaType tipo, boolean patch) throws Exception {
        var b = patch ? patch(caminho) : post(caminho);
        return mvc.perform(b.header("Authorization", "Bearer " + c.token()).contentType(tipo).content(corpo)).andReturn();
    }

    private void naoE500(MvcResult r, String ctx) throws Exception {
        int s = r.getResponse().getStatus();
        assertTrue(s < 500, ctx + " -> " + s + " " + r.getResponse().getContentAsString());
    }

    @Test
    void confirmarComVencimentoDevolvePagoFalsoENuloQuandoCancelada() throws Exception {
        Conta c = conta();
        String venc = "\"" + LocalDate.now(relogioBrasilia).plusDays(7) + "\"";
        MvcResult ok = enviar(c, "/compras/confirmar", corpo(c, "false", "null", "null", venc), MediaType.APPLICATION_JSON, false);
        assertEquals(201, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
        JsonNode compra = json.readTree(ok.getResponse().getContentAsString()).get("compra");
        assertEquals(false, compra.get("pago").asBoolean());
        String id = compra.get("id").asText();
        MvcResult canc = enviar(c, "/compras/" + id + "/cancelar", "{\"observacao\":\"Cancelada por engano, fornecedor trocou o pedido.\"}",
                MediaType.APPLICATION_JSON, false);
        assertEquals(200, canc.getResponse().getStatus(), canc.getResponse().getContentAsString());
        assertTrue(json.readTree(canc.getResponse().getContentAsString()).get("compra").get("pago").isNull());
    }

    @Test
    void semVencimentoNaoPagaVira400ComMensagem() throws Exception {
        Conta c = conta();
        MvcResult r = enviar(c, "/compras/confirmar", corpo(c, "false", "null", "null", "null"), MediaType.APPLICATION_JSON, false);
        assertTrue(r.getResponse().getStatus() == 400 || r.getResponse().getStatus() == 422, r.getResponse().getContentAsString());
        assertTrue(r.getResponse().getContentAsString().contains("Informe quando esta compra vence."));
    }

    @Test
    void entradaHostilNaConfirmacaoNuncaE500() throws Exception {
        Conta c = conta();
        String metodo = "\"" + c.credito() + "\"";
        String[] vencimentos = {"\"2025-02-31\"", "\"1999-12-31\"", "\"2101-01-01\"", "\"+999999999-12-31\"", "\"abc\"", "123", "[]", "{}", "\"\""};
        for (String v : vencimentos) {
            naoE500(enviar(c, "/compras/confirmar", corpo(c, "false", "null", "null", v), MediaType.APPLICATION_JSON, false), "venc=" + v);
            naoE500(enviar(c, "/compras/confirmar", corpo(c, "true", metodo, "2", v), MediaType.APPLICATION_JSON, false), "credito venc=" + v);
        }
        for (String p : new String[]{"0", "-1", "25", "61", "2147483648", "99999999999999999999", "1.5", "\"x\"", "[]"}) {
            naoE500(enviar(c, "/compras/confirmar", corpo(c, "true", metodo, p, "null"), MediaType.APPLICATION_JSON, false), "parcelas=" + p);
        }
        naoE500(enviar(c, "/compras/confirmar", corpo(c, "true", "\"" + UUID.randomUUID() + "\"", "2", "null"), MediaType.APPLICATION_JSON, false), "metodo de outra conta");
        naoE500(enviar(c, "/compras/confirmar", "{\"vencimento\":", MediaType.APPLICATION_JSON, false), "json quebrado");
        naoE500(enviar(c, "/compras/confirmar", corpo(c, "false", "null", "null", "null"), MediaType.TEXT_PLAIN, false), "content-type");
        naoE500(enviar(c, "/compras/confirmar", "", MediaType.APPLICATION_JSON, false), "corpo vazio");
    }

    @Test
    void entradaHostilNoPagamentoNuncaE500() throws Exception {
        Conta c = conta();
        String venc = "\"" + LocalDate.now(relogioBrasilia).plusDays(7) + "\"";
        MvcResult ok = enviar(c, "/compras/confirmar", corpo(c, "false", "null", "null", venc), MediaType.APPLICATION_JSON, false);
        String id = json.readTree(ok.getResponse().getContentAsString()).get("compra").get("id").asText();
        String url = "/compras/" + id + "/pagamento";
        for (String v : new String[]{"\"2025-02-31\"", "\"1999-12-31\"", "\"2101-01-01\"", "\"abc\"", "123", "null"}) {
            naoE500(enviar(c, url, "{\"pago\":false,\"vencimento\":" + v + "}", MediaType.APPLICATION_JSON, true), "venc=" + v);
        }
        for (String p : new String[]{"0", "-1", "61", "2147483648", "\"x\""}) {
            naoE500(enviar(c, url, "{\"pago\":true,\"metodoPagamentoId\":\"" + c.credito() + "\",\"parcelas\":" + p + "}", MediaType.APPLICATION_JSON, true), "parcelas=" + p);
        }
        naoE500(enviar(c, url, "{", MediaType.APPLICATION_JSON, true), "json quebrado");
        naoE500(enviar(c, url, "{}", MediaType.TEXT_PLAIN, true), "content-type");
        MvcResult outra = enviar(c, "/compras/" + UUID.randomUUID() + "/pagamento", "{\"pago\":false,\"vencimento\":" + venc + "}", MediaType.APPLICATION_JSON, true);
        assertEquals(404, outra.getResponse().getStatus());
        MvcResult lixo = enviar(c, "/compras/nao-e-uuid/pagamento", "{\"pago\":false}", MediaType.APPLICATION_JSON, true);
        naoE500(lixo, "id invalido");
    }
}
