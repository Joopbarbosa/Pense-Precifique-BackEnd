package com.penseprecifique.api.pdf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Épico #89, Passo 5/6 — fim a fim de verdade: orçamento real no banco (via
 * {@code OrcamentoService}, mesmo caminho de produção) até {@code PdfService.gerarPdfOrcamento}
 * chamando o microsserviço via HTTP (WireMock no lugar do pense-precifique-pdf real). Os
 * cenários de erro por status HTTP já são cobertos em detalhe por {@link PdfMicroservicoClientIT}
 * — aqui só confirma que a fiação Spring (properties, injeção do client, transação) funciona.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PdfServiceOrcamentoMicroservicoIT {

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

    @Autowired PdfService pdfService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    @BeforeEach
    void resetarStubsEToken() {
        wireMockServer.resetAll();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-e2e-teste");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limparRequisicaoFalsa() {
        RequestContextHolder.resetRequestAttributes();
    }

    private UUID criarOrcamentoSimples() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("pdf-e2e-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente E2E").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo de Teste").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).precoVenda(new BigDecimal("50.00")).build());

        OrcamentoItemRequest itemReq = new OrcamentoItemRequest();
        itemReq.setProdutoId(produto.getId());
        itemReq.setMargemAplicada(new BigDecimal("50"));
        itemReq.setPrecoUnitario(new BigDecimal("50.00"));
        itemReq.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemReq));

        OrcamentoDetalheResponse criado = orcamentoService.criar(req);
        return criado.getId();
    }

    @Test
    @Transactional
    void gerarPdfOrcamentoChamaOMicroservicoERetornaOsBytesDoPdf() {
        UUID orcamentoId = criarOrcamentoSimples();
        byte[] pdfFalso = "%PDF-1.4 conteudo falso e2e".getBytes(StandardCharsets.UTF_8);

        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] resultado = pdfService.gerarPdfOrcamento(orcamentoId);

        assertArrayEquals(pdfFalso, resultado);
    }

    @Test
    @Transactional
    void gerarPdfOrcamentoPropagaMensagemAmigavelQuandoMicroservicoFalha() {
        UUID orcamentoId = criarOrcamentoSimples();

        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(503)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarPdfOrcamento(orcamentoId));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }

    @Test
    @Transactional
    void gerarPreviewHtmlOrcamentoChamaOMicroservicoComFormatHtmlERetornaOHtml() {
        UUID orcamentoId = criarOrcamentoSimples();
        String htmlFalso = "<html><body>Preview e2e</body></html>";

        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(htmlFalso)));

        String resultado = pdfService.gerarPreviewHtmlOrcamento(orcamentoId);

        assertEquals(htmlFalso, resultado);
        wireMockServer.verify(postRequestedFor(urlPathMatching("/render/orcamento/" + orcamentoId + ".*"))
                .withQueryParam("format", equalTo("html")));
    }

    @Test
    @Transactional
    void gerarPreviewHtmlOrcamentoPropagaMensagemAmigavelQuandoMicroservicoFalha() {
        UUID orcamentoId = criarOrcamentoSimples();

        wireMockServer.stubFor(post(urlPathMatching("/render/orcamento/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(503)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarPreviewHtmlOrcamento(orcamentoId));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }
}
