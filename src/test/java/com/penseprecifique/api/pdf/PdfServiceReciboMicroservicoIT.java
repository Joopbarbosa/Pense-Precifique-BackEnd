package com.penseprecifique.api.pdf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #248 (Frente A) — fim a fim de verdade para os 3 documentos migrados nesta rodada, mesmo padrão
 * de {@link PdfServiceOrcamentoMicroservicoIT} (#89): orçamento real no banco até
 * {@code PdfService.gerar*} chamar o microsserviço via HTTP (WireMock). Guards de negócio
 * (status/cancelamentoTipo/estornoSinal) preservados exatamente como no fluxo antigo — só a fonte
 * do PDF muda (microsserviço em vez de OpenHTMLToPDF local).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PdfServiceReciboMicroservicoIT {

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
    @Autowired OrcamentoRepository orcamentoRepository;
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
                .email("pdf-recibo-e2e-" + UUID.randomUUID() + "@test.com")
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
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemReq));

        OrcamentoDetalheResponse criado = orcamentoService.criar(req);
        return criado.getId();
    }

    private Orcamento buscarParaEditar(UUID orcamentoId) {
        return orcamentoRepository.findById(orcamentoId).orElseThrow();
    }

    @Test
    @Transactional
    void gerarReciboSinalChamaOMicroservicoQuandoStatusPermiteERetornaOsBytes() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = buscarParaEditar(orcamentoId);
        orcamento.setStatus(StatusOrcamento.SINAL_PAGO);
        orcamento.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamento.setValorSinal(new BigDecimal("25.00"));
        orcamento.setDataAprovacao(LocalDateTime.now());
        orcamentoRepository.save(orcamento);

        byte[] pdfFalso = "%PDF-1.4 recibo-sinal falso".getBytes(StandardCharsets.UTF_8);
        wireMockServer.stubFor(post(urlPathMatching("/render/recibo-sinal/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] resultado = pdfService.gerarReciboSinal(orcamentoId);

        assertArrayEquals(pdfFalso, resultado);
    }

    @Test
    @Transactional
    void gerarReciboSinalBloqueiaAntesDeChamarOMicroservicoQuandoStatusNaoPermite() {
        UUID orcamentoId = criarOrcamentoSimples(); // nasce em RASCUNHO

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarReciboSinal(orcamentoId));

        assertEquals("Recibo do sinal só disponível a partir do status SINAL_PAGO", ex.getMessage());
        wireMockServer.verify(0, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathMatching("/render/recibo-sinal/.*")));
    }

    @Test
    @Transactional
    void gerarPdfMultaChamaOMicroservicoQuandoCancelamentoTemMultaERetornaOsBytes() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = buscarParaEditar(orcamentoId);
        orcamento.setCancelamentoTipo(TipoCancelamento.MULTA);
        orcamento.setPercentualMulta(new BigDecimal("10"));
        orcamento.setDataAprovacao(LocalDateTime.now());
        orcamentoRepository.save(orcamento);

        byte[] pdfFalso = "%PDF-1.4 multa falso".getBytes(StandardCharsets.UTF_8);
        wireMockServer.stubFor(post(urlPathMatching("/render/pdf-multa/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] resultado = pdfService.gerarPdfMulta(orcamentoId);

        assertArrayEquals(pdfFalso, resultado);
    }

    @Test
    @Transactional
    void gerarPdfMultaBloqueiaQuandoCancelamentoNaoTemMulta() {
        UUID orcamentoId = criarOrcamentoSimples(); // sem cancelamentoTipo

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarPdfMulta(orcamentoId));

        assertEquals("PDF de multa só disponível para cancelamentos com multa", ex.getMessage());
    }

    @Test
    @Transactional
    void gerarReciboEstornoSinalChamaOMicroservicoQuandoEstornoAtivoERetornaOsBytes() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = buscarParaEditar(orcamentoId);
        orcamento.setEstornoSinal(true);
        orcamento.setValorSinal(new BigDecimal("25.00"));
        orcamento.setDataEstornoSinal(LocalDateTime.now());
        orcamentoRepository.save(orcamento);

        byte[] pdfFalso = "%PDF-1.4 estorno falso".getBytes(StandardCharsets.UTF_8);
        wireMockServer.stubFor(post(urlPathMatching("/render/recibo-estorno/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] resultado = pdfService.gerarReciboEstornoSinal(orcamentoId);

        assertArrayEquals(pdfFalso, resultado);
    }

    @Test
    @Transactional
    void gerarReciboEstornoSinalBloqueiaQuandoEstornoNaoAtivo() {
        UUID orcamentoId = criarOrcamentoSimples(); // estornoSinal null

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarReciboEstornoSinal(orcamentoId));

        assertEquals("Recibo de estorno só disponível para cancelamentos com estorno de sinal", ex.getMessage());
    }

    @Test
    @Transactional
    void gerarPdfMultaPropagaMensagemAmigavelQuandoMicroservicoFalha() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = buscarParaEditar(orcamentoId);
        orcamento.setCancelamentoTipo(TipoCancelamento.MULTA);
        orcamento.setPercentualMulta(new BigDecimal("10"));
        orcamentoRepository.save(orcamento);

        wireMockServer.stubFor(post(urlPathMatching("/render/pdf-multa/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(503)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarPdfMulta(orcamentoId));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }
}
