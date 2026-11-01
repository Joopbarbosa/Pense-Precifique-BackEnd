package com.penseprecifique.api.pdf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Épico #89, Passo 4/6 — {@code GET /orcamentos/{id}/preview-html} fim a fim via MockMvc
 * (não só {@code PdfService} isolado): o handler declara {@code produces = text/html} no
 * sucesso, e o Spring MVC registra os "producible media types" da rota mapeada como atributo
 * de requisição ANTES de invocar o método — {@code ExceptionHandlerExceptionResolver} reusa
 * esse atributo para negociar o corpo de erro do {@code GlobalExceptionHandler}
 * ({@code ResponseEntity<ErrorResponseDTO>}, sempre JSON). Se o controller declarasse
 * {@code produces} restrito sem esse cuidado, o corpo de erro correria risco de virar 406
 * silencioso em vez do JSON amigável — este teste prova que não é o caso aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoPreviewHtmlIT {

    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void configurarUrlDoMicroservicoFalso(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        registry.add("pdf.microservice.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void pararServidorFalso() {
        wireMockServer.stop();
    }

    @Autowired MockMvc mockMvc;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void resetarStubs() {
        wireMockServer.resetAll();
    }

    private record ContextoOrcamento(UUID orcamentoId, String token) {}

    private ContextoOrcamento criarOrcamentoSimples() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("preview-html-it-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Preview HTML").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo Preview").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).precoVenda(new BigDecimal("50.00")).build());

        OrcamentoItemRequest itemReq = new OrcamentoItemRequest();
        itemReq.setProdutoId(produto.getId());
        itemReq.setMargemAplicada(new BigDecimal("50"));
        itemReq.setPrecoUnitario(new BigDecimal("50.00"));
        itemReq.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemReq));

        OrcamentoDetalheResponse criado = orcamentoService.criar(req);
        String token = jwtTokenProvider.generateToken(usuario);
        return new ContextoOrcamento(criado.getId(), token);
    }

    @Test
    @Transactional
    void sucessoRetornaHtmlDoMicroservico() throws Exception {
        ContextoOrcamento ctx = criarOrcamentoSimples();
        String htmlFalso = "<html><body>Orçamento de teste</body></html>";

        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/" + ctx.orcamentoId() + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(htmlFalso)));

        mockMvc.perform(get("/orcamentos/{id}/preview-html", ctx.orcamentoId())
                        .header("Authorization", "Bearer " + ctx.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(htmlFalso));
    }

    @Test
    @Transactional
    void microservicoIndisponivelRetornaErroJsonAmigavelNaoNegociacao406() throws Exception {
        ContextoOrcamento ctx = criarOrcamentoSimples();

        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/" + ctx.orcamentoId() + ".*"))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(get("/orcamentos/{id}/preview-html", ctx.orcamentoId())
                        .header("Authorization", "Bearer " + ctx.token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value(
                        "Geração de documento temporariamente indisponível. Tente novamente em instantes."));
    }

    @Test
    @Transactional
    void avisoDeOnzePaginasERepeticaoConfirmadaEntregamDuasPartes() throws Exception {
        ContextoOrcamento ctx = criarOrcamentoSimples();
        String base64 = java.util.Base64.getEncoder().encodeToString("%PDF-parte".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/.*"))
                .withQueryParam("dividir", equalTo("false"))
                .willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withHeader("X-Pdf-Page-Count", "11")
                        .withBody("{\"tipo\":\"AVISO\",\"message\":\"Este documento tem mais de 10 páginas.\",\"totalPaginas\":11,\"dividirDisponivel\":true}")));
        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/.*"))
                .withQueryParam("dividir", equalTo("true"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withHeader("X-Pdf-Page-Count", "11")
                        .withBody("{\"totalPaginas\":11,\"partes\":[{\"numeroParte\":1,\"totalPaginas\":6,\"pdfBase64\":\"" + base64
                                + "\"},{\"numeroParte\":2,\"totalPaginas\":5,\"pdfBase64\":\"" + base64 + "\"}]}")));

        mockMvc.perform(get("/orcamentos/{id}/pdf", ctx.orcamentoId()).header("Authorization", "Bearer " + ctx.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.tipo").value("AVISO"))
                .andExpect(jsonPath("$.totalPaginas").value(11)).andExpect(jsonPath("$.dividirDisponivel").value(true));
        mockMvc.perform(get("/orcamentos/{id}/pdf", ctx.orcamentoId()).param("dividir", "true")
                        .header("Authorization", "Bearer " + ctx.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPaginas").value(11))
                .andExpect(jsonPath("$.partes[0].numeroParte").value(1)).andExpect(jsonPath("$.partes[0].totalPaginas").value(6))
                .andExpect(jsonPath("$.partes[1].numeroParte").value(2)).andExpect(jsonPath("$.partes[1].totalPaginas").value(5));
    }

    @Test
    @Transactional
    void entradaHostilDoDownloadERespostaIdorNaoRevelamDados() throws Exception {
        ContextoOrcamento ctx = criarOrcamentoSimples();
        mockMvc.perform(get("/orcamentos/not-a-uuid/pdf").header("Authorization", "Bearer " + ctx.token()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orcamentos/{id}/pdf", ctx.orcamentoId()).param("dividir", "5e999")
                        .header("Authorization", "Bearer " + ctx.token()))
                .andExpect(status().isBadRequest());

        Usuario outra = usuarioRepository.save(Usuario.builder().email("pdf-outro-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        String tokenOutraConta = jwtTokenProvider.generateToken(outra);
        String idInexistente = UUID.randomUUID().toString();
        var idor = mockMvc.perform(get("/orcamentos/{id}/pdf", ctx.orcamentoId())
                        .header("Authorization", "Bearer " + tokenOutraConta)).andReturn().getResponse();
        var inexistente = mockMvc.perform(get("/orcamentos/{id}/pdf", idInexistente)
                        .header("Authorization", "Bearer " + tokenOutraConta)).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(404, idor.getStatus());
        var jackson = new com.fasterxml.jackson.databind.ObjectMapper();
        org.junit.jupiter.api.Assertions.assertEquals(
                jackson.readTree(idor.getContentAsString()).get("message").asText(),
                jackson.readTree(inexistente.getContentAsString()).get("message").asText());
    }
}
