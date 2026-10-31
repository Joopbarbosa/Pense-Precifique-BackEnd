package com.penseprecifique.api.orcamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.testsupport.MetodosPagamentoTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PP-528 (RN-NOVA-38/39/42, CEN-NOVO-45/59): validação do passo Pagamento por HTTP, com entrada hostil.
 * Nada é gravado; toda recusa é 4xx e nunca 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoPagamentoValidarHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;
    private final ObjectMapper json = new ObjectMapper();

    private String token;
    private Usuario usuario;
    private UUID orcamentoId;
    private UUID pixId;
    private UUID metodoDeOutraEmpresaId;

    private String registrar(String prefixo) throws Exception {
        String email = prefixo + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        return email;
    }

    @BeforeEach
    void preparar() throws Exception {
        String emailB = registrar("pag-b-");
        Usuario outra = usuarios.findByEmailAndDeletedAtIsNull(emailB).orElseThrow();
        metodoDeOutraEmpresaId = MetodosPagamentoTeste.criar(metodos, outra, TipoMetodoPagamento.DINHEIRO, true).getId();

        String email = registrar("pag-a-");
        usuario = usuarios.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        pixId = MetodosPagamentoTeste.pix(metodos, usuario).getId();
        Cliente cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria").ehCliente(true).ativa(true).build());
        orcamentoId = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(1)
                .status(StatusOrcamento.ENVIADO).subtotal(new BigDecimal("100.00")).total(new BigDecimal("100.00")).build()).getId();
    }

    private MvcResult chamar(UUID id, String corpo) throws Exception {
        return mvc.perform(post("/orcamentos/" + id + "/pagamento/validar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private String corpo(Object metodo, String resto) {
        return "{\"metodoPagamentoId\":" + (metodo == null ? "null" : "\"" + metodo + "\"") + (resto.isEmpty() ? "" : "," + resto) + "}";
    }

    private String amanha() { return "\"" + LocalDate.now().plusDays(1) + "\""; }

    @Test
    void semSinalDevolveSaldoIntegral() throws Exception {
        MvcResult r = chamar(orcamentoId, corpo(pixId, "\"temSinal\":false"));
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        assertEquals(0, new BigDecimal("100.00").compareTo(n.get("saldo").decimalValue()));
        assertTrue(n.get("geraParcelaSaldo").asBoolean());
        assertEquals("PIX", n.get("metodoPagamento").get("tipo").asText());
    }

    @Test
    void sinalPadraoDeCinquentaPorCento() throws Exception {
        MvcResult r = chamar(orcamentoId, corpo(pixId, "\"temSinal\":true,\"sinalAte\":" + amanha()));
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        assertEquals(0, new BigDecimal("50.00").compareTo(n.get("valorSinal").decimalValue()));
        assertEquals(0, new BigDecimal("50.00").compareTo(n.get("saldo").decimalValue()));
    }

    @Test
    void validarNaoGravaNada() throws Exception {
        chamar(orcamentoId, corpo(pixId, "\"temSinal\":true,\"sinalAte\":" + amanha()));
        Orcamento o = orcamentos.findById(orcamentoId).orElseThrow();
        assertNull(o.getMetodoPagamento());
        assertEquals(StatusOrcamento.ENVIADO, o.getStatus());
        assertNull(o.getValorSinal());
    }

    @Test
    void formaDeOutraEmpresaEInexistenteTemRespostaIdentica() throws Exception {
        MvcResult alheio = chamar(orcamentoId, corpo(metodoDeOutraEmpresaId, "\"temSinal\":false"));
        MvcResult inexistente = chamar(orcamentoId, corpo(UUID.randomUUID(), "\"temSinal\":false"));
        assertEquals(400, alheio.getResponse().getStatus());
        assertEquals(alheio.getResponse().getStatus(), inexistente.getResponse().getStatus());
        String semHora = "\"timestamp\":\"[^\"]*\"";
        assertEquals(alheio.getResponse().getContentAsString().replaceAll(semHora, ""),
                inexistente.getResponse().getContentAsString().replaceAll(semHora, ""));
        assertTrue(alheio.getResponse().getContentAsString().contains("Escolha uma forma de pagamento da lista."));
    }

    @Test
    void formaInativaEAusenteSaoRecusadas() throws Exception {
        var m = metodos.findById(pixId).orElseThrow();
        m.setAtivo(false);
        metodos.save(m);
        assertEquals(400, chamar(orcamentoId, corpo(pixId, "\"temSinal\":false")).getResponse().getStatus());
        assertEquals(400, chamar(orcamentoId, corpo(null, "\"temSinal\":false")).getResponse().getStatus());
        assertEquals(400, chamar(orcamentoId, "{}").getResponse().getStatus());
    }

    @Test
    void orcamentoInexistenteOuForaDeEnviadoNaoVale() throws Exception {
        assertEquals(404, chamar(UUID.randomUUID(), corpo(pixId, "\"temSinal\":false")).getResponse().getStatus());
        Orcamento o = orcamentos.findById(orcamentoId).orElseThrow();
        o.setStatus(StatusOrcamento.RASCUNHO);
        orcamentos.save(o);
        int s = chamar(orcamentoId, corpo(pixId, "\"temSinal\":false")).getResponse().getStatus();
        assertTrue(s == 400 || s == 409, "status " + s);
    }

    @Test
    void semTokenNaoEntra() throws Exception {
        int s = mvc.perform(post("/orcamentos/" + orcamentoId + "/pagamento/validar")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "status " + s);
    }

    @Test
    void entradaHostilNuncaDa500() throws Exception {
        String longo = "a".repeat(100_000);
        String[] corpos = {
                "", "{", "[]", "null", "\"texto\"", "{\"metodoPagamentoId\":123}", "{\"metodoPagamentoId\":\"nao-e-uuid\"}",
                corpo(pixId, "\"temSinal\":\"talvez\""),
                corpo(pixId, "\"temSinal\":true,\"tipoSinal\":\"OUTRO\",\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"percentualSinal\":1E+999999999,\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"percentualSinal\":-1E+999999999,\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"percentualSinal\":\"abc\",\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"percentualSinal\":12.345,\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"tipoSinal\":\"VALOR\",\"valorSinal\":99999999999999999999999.99,\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"tipoSinal\":\"VALOR\",\"valorSinal\":0.001,\"sinalAte\":" + amanha()),
                corpo(pixId, "\"temSinal\":true,\"sinalAte\":\"2026-02-30\""),
                corpo(pixId, "\"temSinal\":true,\"sinalAte\":\"31/12/2026\""),
                corpo(pixId, "\"temSinal\":true,\"sinalAte\":\"+999999999-12-31\""),
                corpo(pixId, "\"temSinal\":false,\"vencimentoSaldo\":\"0000-00-00\""),
                corpo(pixId, "\"temSinal\":false,\"vencimentoSaldo\":\"1999-12-31\""),
                corpo(pixId, "\"temSinal\":false,\"vencimentoSaldo\":\"" + longo + "\""),
                corpo(pixId, "\"temSinal\":false,\"extra\":\"" + longo + "\""),
                "{\"metodoPagamentoId\":\"" + pixId + "\u0000\"}",
                corpo(pixId, "\"temSinal\":true,\"percentualSinal\":5\u00000,\"sinalAte\":" + amanha()),
        };
        for (String c : corpos) {
            MvcResult r = chamar(orcamentoId, c);
            int s = r.getResponse().getStatus();
            assertTrue(s >= 400 && s < 500 || s == 200, "corpo '" + c.substring(0, Math.min(c.length(), 120)) + "' -> " + s);
        }
        // content-type errado
        int s = mvc.perform(post("/orcamentos/" + orcamentoId + "/pagamento/validar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.TEXT_PLAIN).content("x")).andReturn().getResponse().getStatus();
        assertTrue(s >= 400 && s < 500, "status " + s);
        // id de rota malformado
        int s2 = mvc.perform(post("/orcamentos/nao-e-uuid/pagamento/validar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
        assertTrue(s2 >= 400 && s2 < 500, "status " + s2);
    }
}
