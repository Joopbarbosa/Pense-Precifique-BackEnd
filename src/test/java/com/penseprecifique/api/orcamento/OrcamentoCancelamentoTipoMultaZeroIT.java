package com.penseprecifique.api.orcamento;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.pdf.PdfService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RN-NOVA-28 (revisão de ORC-010, #317) — cenário de regressão CEN-NOVO-42: a artesã escolhe
 * "Não" no toggle de multa (envia {@code percentualMulta = 0}) ao cancelar um orçamento a partir
 * de EM_PRODUCAO/FINALIZADO. {@code cancelamentoTipo} deve continuar {@code MULTA} (o switch de
 * {@link OrcamentoService#cancelar} decide pelo status de origem, nunca pelo valor do percentual),
 * exposto corretamente em {@code GET /orcamentos/{id}} (mesmo caminho de {@link
 * OrcamentoService#buscarPorId}) e sem bloquear a geração do PDF de multa (gate de {@code
 * ReciboPdfPayloadService#montarPayloadPdfMulta} compara só {@code cancelamentoTipo}, nunca {@code
 * percentualMulta}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoCancelamentoTipoMultaZeroIT {

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

    @Autowired OrcamentoService orcamentoService;
    @Autowired PdfService pdfService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Cliente cliente;

    @BeforeEach
    void resetarStubsEToken() {
        wireMockServer.resetAll();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-e2e-teste");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-multa-zero-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Multa Zero").ativa(true).build());
    }

    private Produto novoProduto(int numero) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("300.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private UUID criarOrcamento(Produto produto) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("0"));
        item.setPrecoUnitario(new BigDecimal("300.00"));
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(true);
        req.setValorSinal(new BigDecimal("100.00"));

        return orcamentoService.criar(req).getId();
    }

    private void vincularNovaProducao(UUID orcamentoId) {
        Producao producao = producaoRepository.save(Producao.builder().usuario(usuario).numero(1).build());
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** RASCUNHO -> ... -> SINAL_PAGO -> EM_PRODUCAO (sinal pago, pré-requisito do mini-estorno de RN-NOVA-1). */
    private void avancarAteEmProducao(UUID orcamentoId) {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        AvancaStatusRequest sinalReq = new AvancaStatusRequest();
        sinalReq.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // APROVADO -> AGUARDANDO_SINAL
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // AGUARDANDO_SINAL -> SINAL_PAGO
        vincularNovaProducao(orcamentoId); // RN-NOVA-6 — pré-requisito da transição para EM_PRODUCAO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // SINAL_PAGO -> EM_PRODUCAO
    }

    @Test
    void percentualMultaZeroMantemCancelamentoTipoMultaExpostoNoDetalheEPermitePdf() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1);
        UUID orcamentoId = criarOrcamento(produto);
        avancarAteEmProducao(orcamentoId);

        AvancaStatusRequest cancelReq = new AvancaStatusRequest();
        cancelReq.setPercentualMulta(BigDecimal.ZERO); // artesã escolhe "Não" no toggle de multa
        cancelReq.setMotivoCancelamento("Cancelado a pedido da cliente, sem cobrança de multa");
        OrcamentoDetalheResponse resultadoCancelamento = orcamentoService.cancelar(orcamentoId, cancelReq);

        // cancelamentoTipo continua MULTA mesmo com percentualMulta = 0 — RN-NOVA-28.
        assertEquals(TipoCancelamento.MULTA, resultadoCancelamento.getCancelamentoTipo());
        assertEquals(0, BigDecimal.ZERO.compareTo(resultadoCancelamento.getValorMulta()));

        // GET /orcamentos/{id} (OrcamentoService.buscarPorId, mesmo mapper de cancelar()) expõe o mesmo valor.
        OrcamentoDetalheResponse detalhe = orcamentoService.buscarPorId(orcamentoId);
        assertEquals(TipoCancelamento.MULTA, detalhe.getCancelamentoTipo());

        // Geração do PDF de multa não é bloqueada por percentualMulta = 0 (gate só olha cancelamentoTipo).
        byte[] pdfFalso = "%PDF-1.4 multa-zero falso".getBytes(StandardCharsets.UTF_8);
        wireMockServer.stubFor(post(urlPathMatching("/render/pdf-multa/" + orcamentoId + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] pdfGerado = pdfService.gerarPdfMulta(orcamentoId);

        assertArrayEquals(pdfFalso, pdfGerado);
    }
}
