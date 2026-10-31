package com.penseprecifique.api.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoService;
import com.penseprecifique.api.caixa.VendaCaixaService;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.caixa.*;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Apoio dos ITs do WhatsApp (#1170): Evolution e microsserviço de PDF falsos (WireMock), nunca serviço real. */
abstract class WhatsappTestBase {

    static final WireMockServer FALSO = new WireMockServer(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort());
    static final String SENHA = "senha-valida-123";
    static final String CHAVE_FALSA = "chave-falsa-de-teste";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired CaixaTurnoService caixaTurnoService;
    @Autowired VendaCaixaService vendaCaixaService;
    @Autowired MetodoPagamentoConfiguravelService metodos;
    @Autowired WhatsappEnvioRepository envios;
    final ObjectMapper json = new ObjectMapper();

    record Conta(String token, Usuario usuario) { }

    @BeforeEach
    void stubs() {
        FALSO.resetAll();
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/render/recibo-caixa/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf")
                        .withBody("%PDF-recibo".getBytes(StandardCharsets.UTF_8))));
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/instance/create"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/instance/connect/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"base64\":\"data:image/png;base64,QRFALSO\"}")));
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/instance/connectionState/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"instance\":{\"state\":\"open\"}}")));
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/message/sendMedia/.*"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathMatching("/instance/logout/.*"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));
    }

    Conta registrar(String prefixo) throws Exception {
        String email = prefixo + "-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA
                        + "\",\"nomeEmpresa\":\"Ateliê da Ana\"}")).andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return new Conta(n.get("token").asText(), usuarios.findByEmailAndDeletedAtIsNull(email).orElseThrow());
    }

    /** Conta que já passou pelo aceite de risco e escaneou o QR (canal falso responde "open"). */
    Conta contaConectada(String prefixo) throws Exception {
        Conta c = registrar(prefixo);
        mvc.perform(post("/whatsapp/conexao").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"entendiRiscos\":true}")).andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/whatsapp/status")
                .header("Authorization", "Bearer " + c.token())).andReturn();
        return c;
    }

    VendaCaixaResponseDTO vender(Conta c) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(c.usuario().getEmail(), null, List.of()));
        Produto p = produtos.save(Produto.builder().usuario(c.usuario()).numero(1).nome("Laço pequeno")
                .tipo(TipoProduto.PRODUTO).tempoProducao(1).rendimento(BigDecimal.ONE)
                .precoVenda(new BigDecimal("15.00")).estoqueAtual(new BigDecimal("10"))
                .permitirEstoqueNegativo(true).ativo(true).build());
        UUID dinheiro = metodos.listar().stream().filter(m -> m.tipo() == TipoMetodoPagamento.DINHEIRO)
                .findFirst().orElseThrow().id();
        caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        return (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(null,
                List.of(new VendaCaixaItemRequestDTO(null, p.getId(), new BigDecimal("2"), null)), null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiro, new BigDecimal("50.00"), null)), null));
    }

    Cliente cliente(Conta c, String nome, String whatsapp, boolean aceita, boolean recusou) {
        String canonico = NumeroWhatsapp.canonico(whatsapp);
        return clientes.save(Cliente.builder().usuario(c.usuario()).numero((int) (Math.random() * 100000)).nome(nome)
                .ehCliente(true).ativa(true).whatsapp(whatsapp).whatsappCanonico(canonico)
                .aceitaWhatsapp(aceita).recusouWhatsapp(recusou).build());
    }

    org.springframework.test.web.servlet.ResultActions enviar(Conta c, String corpo) throws Exception {
        return mvc.perform(post("/whatsapp/envios").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    String corpoEnvio(UUID doc, String extra) {
        return "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"" + doc + "\"" + (extra == null ? "" : "," + extra) + "}";
    }
}
