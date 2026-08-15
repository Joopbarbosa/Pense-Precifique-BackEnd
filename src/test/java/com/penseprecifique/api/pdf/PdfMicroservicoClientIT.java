package com.penseprecifique.api.pdf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoDocumentoOrcamentoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoEmpresaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Épico #89, Passo 5 — {@code PdfMicroservicoClient} isolado, sem contexto Spring (construído
 * direto, é um POJO comum) e sem banco: WireMock substitui o microsserviço pense-precifique-pdf
 * de verdade pra exercitar a tradução de status HTTP em {@code BusinessException} (Passo 3).
 */
class PdfMicroservicoClientIT {

    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    private static final UUID ORCAMENTO_ID = UUID.fromString("e5f5c3a0-0000-0000-0000-000000000099");

    private PdfMicroservicoClient client;

    @BeforeAll
    static void iniciarServidorFalso() {
        wireMockServer.start();
    }

    @AfterAll
    static void pararServidorFalso() {
        wireMockServer.stop();
    }

    @BeforeEach
    void configurarClienteEToken() {
        wireMockServer.resetAll();
        client = new PdfMicroservicoClient("http://localhost:" + wireMockServer.port(), 5);

        // PdfMicroservicoClient lê o Authorization da requisição atual via RequestContextHolder —
        // fora de um dispatch real de Controller, precisa ser simulado manualmente no teste.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-de-teste-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limparRequisicaoFalsa() {
        RequestContextHolder.resetRequestAttributes();
    }

    private PdfMicroservicoOrcamentoPayload payloadMinimo() {
        return PdfMicroservicoOrcamentoPayload.builder()
                .empresa(PdfMicroservicoEmpresaPayload.builder().nome("Studio").build())
                .documento(PdfMicroservicoDocumentoOrcamentoPayload.builder()
                        .numeroFormatado("47")
                        .nomeCliente("Cliente Teste")
                        .sinalAtivo(false)
                        .itens(List.of())
                        .build())
                .build();
    }

    @Test
    void sucessoRetornaBytesDoPdfERepassaOTokenSemPrefixoBearer() {
        byte[] pdfFalso = "%PDF-1.4 conteudo falso de teste".getBytes(StandardCharsets.UTF_8);
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] resultado = client.gerarPdf("orcamento", ORCAMENTO_ID, payloadMinimo());

        assertArrayEquals(pdfFalso, resultado);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .withHeader("X-User-Token", equalTo("token-de-teste-123"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void microservicoIndisponivel503LancaBusinessExceptionAmigavel() {
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .willReturn(aResponse().withStatus(503)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.gerarPdf("orcamento", ORCAMENTO_ID, payloadMinimo()));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }

    @Test
    void timeoutDoRenderDoMicroservico408LancaMesmaMensagemAmigavelDoIndisponivel() {
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .willReturn(aResponse().withStatus(408)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.gerarPdf("orcamento", ORCAMENTO_ID, payloadMinimo()));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }

    @Test
    void falhaDeConexaoLancaMesmaMensagemAmigavelDoIndisponivel() {
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.gerarPdf("orcamento", ORCAMENTO_ID, payloadMinimo()));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }

    @Test
    void limiteDePaginas413RepassaAMensagemEspecificaAoUsuario() {
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .willReturn(aResponse().withStatus(413)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Documento muito extenso. Entre em contato com o suporte.\"}")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.gerarPdf("orcamento", ORCAMENTO_ID, payloadMinimo()));

        assertEquals("Documento muito extenso. Entre em contato com o suporte.", ex.getMessage());
    }

    @Test
    void gerarHtmlSucessoRetornaStringDoMicroservicoComFormatHtml() {
        String htmlFalso = "<html><body>Preview de teste</body></html>";
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=html"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(htmlFalso)));

        String resultado = client.gerarHtml("orcamento", ORCAMENTO_ID, payloadMinimo());

        assertEquals(htmlFalso, resultado);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=html"))
                .withHeader("X-User-Token", equalTo("token-de-teste-123")));
    }

    @Test
    void gerarHtmlMicroservicoIndisponivel503LancaMesmaBusinessExceptionAmigavelDoGerarPdf() {
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=html"))
                .willReturn(aResponse().withStatus(503)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.gerarHtml("orcamento", ORCAMENTO_ID, payloadMinimo()));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }

    @Test
    void payloadRejeitado400NaoVazaDetalheDoMicroservicoAoUsuario() {
        wireMockServer.stubFor(post(urlEqualTo("/render/orcamento/" + ORCAMENTO_ID + "?format=pdf"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Payload inválido\",\"detalhes\":[{\"campo\":\"documento.nomeCliente\"}]}")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.gerarPdf("orcamento", ORCAMENTO_ID, payloadMinimo()));

        assertEquals("Erro ao gerar PDF.", ex.getMessage());
    }
}
