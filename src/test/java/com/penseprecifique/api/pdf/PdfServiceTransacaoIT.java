package com.penseprecifique.api.pdf;

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
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * #262 — prova viva de que a chamada HTTP ao microsserviço acontece fora de qualquer
 * {@code @Transactional}: substitui {@link PdfMicroservicoClient} por um mock que captura
 * {@link TransactionSynchronizationManager#isActualTransactionActive()} no exato momento em que
 * é invocado. Antes da correção (#262), a leitura de banco e a chamada HTTP dividiam a mesma
 * transação herdada de {@code @Transactional(readOnly = true)} de classe em {@code PdfService} —
 * essa captura teria vindo {@code true}.
 *
 * <p>Sem {@code @Transactional} no método de teste, de propósito: os dados de setup são
 * commitados de verdade via {@code OrcamentoService} (mesmo padrão sem cleanup de
 * {@code OrcamentoFiltroDataCriacaoIT} — usuário aleatório por teste evita colisão). Um
 * {@code @Transactional} de teste criaria a própria transação ambiente que mascararia o bug
 * (a leitura do bean colaborador se juntaria a ela via {@code REQUIRED}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PdfServiceTransacaoIT {

    @Autowired PdfService pdfService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    @MockBean PdfMicroservicoClient pdfMicroservicoClient;

    @BeforeEach
    void configurarToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-tx-teste");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limparRequisicaoFalsa() {
        RequestContextHolder.resetRequestAttributes();
    }

    private UUID criarOrcamentoSimples() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("pdf-tx-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente TX").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo TX").tipo(TipoProduto.PRODUTO)
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
    void gerarPdfOrcamentoChamaOMicroservicoForaDeQualquerTransacao() {
        UUID orcamentoId = criarOrcamentoSimples();
        AtomicBoolean transacaoAtivaNaChamadaHttp = new AtomicBoolean(true);

        when(pdfMicroservicoClient.gerarPdf(anyString(), any(UUID.class), any(PdfMicroservicoOrcamentoPayload.class)))
                .thenAnswer(invocation -> {
                    transacaoAtivaNaChamadaHttp.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return "pdf-falso".getBytes(StandardCharsets.UTF_8);
                });

        pdfService.gerarPdfOrcamento(orcamentoId);

        assertFalse(transacaoAtivaNaChamadaHttp.get(),
                "chamada HTTP ao microsservico nao deve acontecer dentro de uma transacao aberta");
    }

    @Test
    void gerarPreviewHtmlOrcamentoChamaOMicroservicoForaDeQualquerTransacao() {
        UUID orcamentoId = criarOrcamentoSimples();
        AtomicBoolean transacaoAtivaNaChamadaHttp = new AtomicBoolean(true);

        when(pdfMicroservicoClient.gerarHtml(anyString(), any(UUID.class), any(PdfMicroservicoOrcamentoPayload.class)))
                .thenAnswer(invocation -> {
                    transacaoAtivaNaChamadaHttp.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return "<html></html>";
                });

        pdfService.gerarPreviewHtmlOrcamento(orcamentoId);

        assertFalse(transacaoAtivaNaChamadaHttp.get(),
                "chamada HTTP ao microsservico nao deve acontecer dentro de uma transacao aberta");
    }

    /**
     * #248 (Frente A) — mesma prova para os 3 documentos migrados nesta rodada, via
     * {@link ReciboPdfPayloadService} (bean colaborador único para os 3, ver decisoes-pdf.md).
     */
    @Test
    void gerarReciboSinalChamaOMicroservicoForaDeQualquerTransacao() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = orcamentoRepository.findById(orcamentoId).orElseThrow();
        orcamento.setStatus(com.penseprecifique.api.shared.domain.enums.StatusOrcamento.SINAL_PAGO);
        orcamento.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamento.setValorSinal(new BigDecimal("25.00"));
        orcamentoRepository.save(orcamento);

        AtomicBoolean transacaoAtivaNaChamadaHttp = new AtomicBoolean(true);
        when(pdfMicroservicoClient.gerarPdf(anyString(), any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    transacaoAtivaNaChamadaHttp.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return "pdf-falso".getBytes(StandardCharsets.UTF_8);
                });

        pdfService.gerarReciboSinal(orcamentoId);

        assertFalse(transacaoAtivaNaChamadaHttp.get(),
                "chamada HTTP ao microsservico nao deve acontecer dentro de uma transacao aberta");
    }

    @Test
    void gerarPdfMultaChamaOMicroservicoForaDeQualquerTransacao() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = orcamentoRepository.findById(orcamentoId).orElseThrow();
        orcamento.setCancelamentoTipo(TipoCancelamento.MULTA);
        orcamento.setPercentualMulta(new BigDecimal("10"));
        orcamentoRepository.save(orcamento);

        AtomicBoolean transacaoAtivaNaChamadaHttp = new AtomicBoolean(true);
        when(pdfMicroservicoClient.gerarPdf(anyString(), any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    transacaoAtivaNaChamadaHttp.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return "pdf-falso".getBytes(StandardCharsets.UTF_8);
                });

        pdfService.gerarPdfMulta(orcamentoId);

        assertFalse(transacaoAtivaNaChamadaHttp.get(),
                "chamada HTTP ao microsservico nao deve acontecer dentro de uma transacao aberta");
    }

    @Test
    void gerarReciboEstornoSinalChamaOMicroservicoForaDeQualquerTransacao() {
        UUID orcamentoId = criarOrcamentoSimples();
        Orcamento orcamento = orcamentoRepository.findById(orcamentoId).orElseThrow();
        orcamento.setEstornoSinal(true);
        orcamento.setValorSinal(new BigDecimal("25.00"));
        orcamentoRepository.save(orcamento);

        AtomicBoolean transacaoAtivaNaChamadaHttp = new AtomicBoolean(true);
        when(pdfMicroservicoClient.gerarPdf(anyString(), any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    transacaoAtivaNaChamadaHttp.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return "pdf-falso".getBytes(StandardCharsets.UTF_8);
                });

        pdfService.gerarReciboEstornoSinal(orcamentoId);

        assertFalse(transacaoAtivaNaChamadaHttp.get(),
                "chamada HTTP ao microsservico nao deve acontecer dentro de uma transacao aberta");
    }
}
