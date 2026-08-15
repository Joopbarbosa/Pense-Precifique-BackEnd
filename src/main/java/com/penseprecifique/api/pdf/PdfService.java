package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.ReciboPagamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoPdfMultaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboEstornoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboSinalPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.ReciboPagamentoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfService {

    private final SpringTemplateEngine templateEngine;
    private final OrcamentoRepository orcamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final PdfMapper pdfMapper;
    private final PdfMicroservicoClient pdfMicroservicoClient;
    private final OrcamentoPdfPayloadService orcamentoPdfPayloadService;
    private final ReciboPdfPayloadService reciboPdfPayloadService;

    private byte[] renderizarPdf(String template, Context ctx) {
        String html = templateEngine.process("pdf/" + template, ctx);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao renderizar PDF do template '{}': {} - {}",
                    template, e.getClass().getName(), e.getMessage(), e);
            throw new BusinessException("Erro ao gerar PDF: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Delega ao microsserviço pense-precifique-pdf (fluxo D do PRD) em vez do OpenHTMLToPDF local
     * — {@link #renderizarPdf} e o template Thymeleaf "orcamento" ficam sem uso aqui, mas não são
     * removidos ainda (fallback até o novo fluxo ser validado em uso real; limpeza é tarefa
     * separada). Desde #248, recibo-sinal/pdf-multa/recibo-estorno também migraram (ver
     * {@link #gerarReciboSinal}) — só recibo-pagamento continua no fluxo antigo (entidade extra,
     * {@code ReciboPagamento}, migração própria em tarefa separada).
     *
     * <p>Sem {@code @Transactional} de propósito (#262): a leitura de banco acontece em
     * {@link OrcamentoPdfPayloadService#montarPayloadOrcamento}, um bean injetado (não
     * auto-invocação) com sua própria transação curta, que fecha antes desta chamada HTTP de até
     * 30s — a conexão do pool não fica presa durante a espera do microsserviço.
     */
    public byte[] gerarPdfOrcamento(UUID orcamentoId) {
        PdfMicroservicoOrcamentoPayload payload = orcamentoPdfPayloadService.montarPayloadOrcamento(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("orcamento", orcamentoId, payload);
    }

    /**
     * Preview (Fluxo E do PRD) — mesma montagem de payload de {@link #gerarPdfOrcamento}, só
     * troca a chamada final para {@code format=html}: preview e download vêm da mesma fonte
     * (o microsserviço), sem layout duplicado no frontend. Sem {@code @Transactional} pelo mesmo
     * motivo documentado em {@link #gerarPdfOrcamento}.
     */
    public String gerarPreviewHtmlOrcamento(UUID orcamentoId) {
        PdfMicroservicoOrcamentoPayload payload = orcamentoPdfPayloadService.montarPayloadOrcamento(orcamentoId);
        return pdfMicroservicoClient.gerarHtml("orcamento", orcamentoId, payload);
    }

    /**
     * Migrado ao microsserviço em #248 (Frente A) — mesmo padrão de {@link #gerarPdfOrcamento}
     * (#262): leitura de banco em {@link ReciboPdfPayloadService}, bean injetado, chamada HTTP
     * fora de qualquer {@code @Transactional}. Thymeleaf "recibo-sinal" e o template Java
     * associado ficam sem uso aqui (mesmo tratamento dado ao antigo template de orçamento em #89
     * — limpeza é tarefa separada).
     */
    public byte[] gerarReciboSinal(UUID orcamentoId) {
        PdfMicroservicoReciboSinalPayload payload = reciboPdfPayloadService.montarPayloadReciboSinal(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("recibo-sinal", orcamentoId, payload);
    }

    @Transactional(readOnly = true)
    public byte[] gerarReciboPagamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getStatus() != StatusOrcamento.PAGO) {
            throw new BusinessException("Recibo de pagamento só disponível para orçamentos com status PAGO");
        }

        ReciboPagamento recibo = reciboPagamentoRepository.findByOrcamentoId(orcamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de pagamento não encontrado"));

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);

        Context ctx = new Context();
        ctx.setVariable("dados", pdfMapper.toReciboPagamentoPdfData(orcamento, recibo, empresa));

        return renderizarPdf("recibo-pagamento", ctx);
    }

    /** Migrado ao microsserviço em #248 (Frente A) — mesmo padrão de {@link #gerarReciboSinal}. */
    public byte[] gerarPdfMulta(UUID orcamentoId) {
        PdfMicroservicoPdfMultaPayload payload = reciboPdfPayloadService.montarPayloadPdfMulta(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("pdf-multa", orcamentoId, payload);
    }

    /** Migrado ao microsserviço em #248 (Frente A) — mesmo padrão de {@link #gerarReciboSinal}. */
    public byte[] gerarReciboEstornoSinal(UUID orcamentoId) {
        PdfMicroservicoReciboEstornoPayload payload = reciboPdfPayloadService.montarPayloadReciboEstorno(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("recibo-estorno", orcamentoId, payload);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
