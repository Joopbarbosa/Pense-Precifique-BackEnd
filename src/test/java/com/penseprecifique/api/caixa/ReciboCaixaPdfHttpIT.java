package com.penseprecifique.api.caixa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.caixa.*;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V0.19.0 #1170 (RN-NOVA-33, DT-NOVA-9): recibo da venda do Caixa em PDF. O microsserviço é falso (WireMock); o
 * contrato verificado é o payload enviado, o bloqueio de venda cancelada e o isolamento por conta (nunca 500).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ReciboCaixaPdfHttpIT {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final String SENHA = "senha-valida-123";

    @DynamicPropertySource
    static void falso(DynamicPropertyRegistry registry) {
        wireMock.start();
        registry.add("pdf.microservice.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void parar() {
        wireMock.stop();
    }

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired CaixaTurnoService caixaTurnoService;
    @Autowired VendaCaixaService vendaCaixaService;
    @Autowired VendaCaixaRepository vendaRepository;
    @Autowired MetodoPagamentoConfiguravelService metodos;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void limpar() {
        wireMock.resetAll();
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/render/recibo-caixa/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf")
                        .withBody("%PDF-recibo".getBytes(StandardCharsets.UTF_8))));
    }

    private record Conta(String token, Usuario usuario) {}

    private Conta registrar(String prefixo) throws Exception {
        String email = prefixo + "-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê da Ana\"}"))
                .andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return new Conta(n.get("token").asText(), usuarios.findByEmailAndDeletedAtIsNull(email).orElseThrow());
    }

    private VendaCaixaResponseDTO vender(Conta c, boolean comCliente) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(c.usuario().getEmail(), null, List.of()));
        Cliente cli = comCliente ? clientes.save(Cliente.builder().usuario(c.usuario()).numero(1).nome("Maria Silva").ehCliente(true).ativa(true).build()) : null;
        Produto p = produtos.save(Produto.builder().usuario(c.usuario()).numero(1).nome("Laço pequeno").tipo(TipoProduto.PRODUTO).tempoProducao(1)
                .rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("15.00")).estoqueAtual(new BigDecimal("10")).permitirEstoqueNegativo(true).ativo(true).build());
        UUID dinheiro = metodos.listar().stream().filter(m -> m.tipo() == TipoMetodoPagamento.DINHEIRO).findFirst().orElseThrow().id();
        caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        return (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(cli == null ? null : cli.getId(),
                List.of(new VendaCaixaItemRequestDTO(null, p.getId(), new BigDecimal("2"), null)), null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiro, new BigDecimal("50.00"), null)), null));
    }

    @Test
    void vendaConcluidaGeraPdfComPayloadDoRecibo() throws Exception {
        Conta c = registrar("recibo");
        VendaCaixaResponseDTO venda = vender(c, true);
        MvcResult r = mvc.perform(get("/caixa/vendas/" + venda.id() + "/recibo").header("Authorization", "Bearer " + c.token())).andReturn();

        assertEquals(200, r.getResponse().getStatus());
        assertEquals("application/pdf", r.getResponse().getContentType());
        assertEquals("%PDF-recibo", r.getResponse().getContentAsString());
        String corpo = wireMock.findAll(postRequestedFor(urlPathMatching("/render/recibo-caixa/.*"))).get(0).getBodyAsString();
        JsonNode doc = json.readTree(corpo).get("documento");
        assertEquals("Maria Silva", doc.get("nomeCliente").asText());
        assertEquals("R$ 30,00", doc.get("total").asText());
        assertEquals("R$ 20,00", doc.get("troco").asText());
        assertEquals("Dinheiro", doc.get("pagamentos").get(0).get("metodo").asText());
        assertEquals("R$ 50,00", doc.get("pagamentos").get(0).get("valor").asText());
        assertEquals("Laço pequeno", doc.get("itens").get(0).get("nomeProduto").asText());
        assertEquals("2", doc.get("itens").get(0).get("quantidade").asText());
        assertEquals("Ateliê da Ana", json.readTree(corpo).get("empresa").get("nome").asText());
    }

    @Test
    void vendaSemClienteVaiComNomeNulo() throws Exception {
        Conta c = registrar("recibo-anonimo");
        VendaCaixaResponseDTO venda = vender(c, false);
        mvc.perform(get("/caixa/vendas/" + venda.id() + "/recibo").header("Authorization", "Bearer " + c.token()))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus()));
        JsonNode doc = json.readTree(wireMock.findAll(postRequestedFor(urlPathMatching("/render/recibo-caixa/.*"))).get(0).getBodyAsString()).get("documento");
        assertTrue(doc.get("nomeCliente").isNull());
    }

    @Test
    void vendaCanceladaNaoGeraRecibo() throws Exception {
        Conta c = registrar("recibo-cancelada");
        VendaCaixaResponseDTO venda = vender(c, false);
        VendaCaixa v = vendaRepository.findById(venda.id()).orElseThrow();
        v.setStatus(StatusVendaCaixa.CANCELADA);
        vendaRepository.saveAndFlush(v);

        MvcResult r = mvc.perform(get("/caixa/vendas/" + venda.id() + "/recibo").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Esta venda foi cancelada."));
        assertEquals(0, wireMock.findAll(postRequestedFor(urlPathMatching("/render/.*"))).size());
    }

    @Test
    void vendaDeOutraContaEIdInexistenteDaoOMesmo404() throws Exception {
        Conta dona = registrar("dona");
        VendaCaixaResponseDTO venda = vender(dona, false);
        Conta intrusa = registrar("intrusa");

        int outra = mvc.perform(get("/caixa/vendas/" + venda.id() + "/recibo").header("Authorization", "Bearer " + intrusa.token())).andReturn().getResponse().getStatus();
        int inexistente = mvc.perform(get("/caixa/vendas/" + UUID.randomUUID() + "/recibo").header("Authorization", "Bearer " + intrusa.token())).andReturn().getResponse().getStatus();
        assertEquals(404, outra);
        assertEquals(404, inexistente);
        assertEquals(0, wireMock.findAll(postRequestedFor(urlPathMatching("/render/.*"))).size());
    }

    @Test
    void idMalformadoDa400ESemTokenDa401Ou403() throws Exception {
        Conta c = registrar("hostil");
        for (String id : List.of("abc", "1e999", "null", "%00")) {
            int s = mvc.perform(get("/caixa/vendas/" + id + "/recibo").header("Authorization", "Bearer " + c.token())).andReturn().getResponse().getStatus();
            assertTrue(s == 400 || s == 404, "id '" + id + "' deu " + s);
        }
        int semToken = mvc.perform(get("/caixa/vendas/" + UUID.randomUUID() + "/recibo")).andReturn().getResponse().getStatus();
        assertTrue(semToken == 401 || semToken == 403);
    }
}
