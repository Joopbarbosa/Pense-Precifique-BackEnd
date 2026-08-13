package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.ReciboPagamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PdfService {

    private final SpringTemplateEngine templateEngine;
    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final PdfMapper pdfMapper;
    private final PdfMicroservicoClient pdfMicroservicoClient;

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
     * separada). Os outros documentos (recibo-sinal, recibo-pagamento, pdf-multa, recibo-estorno)
     * continuam no fluxo antigo — fora do escopo deste MVP (só orçamento).
     */
    public byte[] gerarPdfOrcamento(UUID orcamentoId) {
        PdfMicroservicoOrcamentoPayload payload = montarPayloadOrcamento(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("orcamento", orcamentoId, payload);
    }

    /**
     * Preview (Fluxo E do PRD) — mesma montagem de payload de {@link #gerarPdfOrcamento}, só
     * troca a chamada final para {@code format=html}: preview e download vêm da mesma fonte
     * (o microsserviço), sem layout duplicado no frontend.
     */
    public String gerarPreviewHtmlOrcamento(UUID orcamentoId) {
        PdfMicroservicoOrcamentoPayload payload = montarPayloadOrcamento(orcamentoId);
        return pdfMicroservicoClient.gerarHtml("orcamento", orcamentoId, payload);
    }

    private PdfMicroservicoOrcamentoPayload montarPayloadOrcamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem = itens.stream()
                .collect(Collectors.toMap(OrcamentoItem::getId,
                        item -> orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId())));

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, empresa, itens, customizacoesPorItem);
        return pdfMapper.toMicroservicoPayload(dados);
    }

    public byte[] gerarReciboSinal(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getStatus().ordinal() < StatusOrcamento.SINAL_PAGO.ordinal()) {
            throw new BusinessException("Recibo do sinal só disponível a partir do status SINAL_PAGO");
        }

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);

        Context ctx = new Context();
        ctx.setVariable("dados", pdfMapper.toReciboPdfData(orcamento, empresa));

        return renderizarPdf("recibo-sinal", ctx);
    }

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

    public byte[] gerarPdfMulta(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getCancelamentoTipo() != TipoCancelamento.MULTA) {
            throw new BusinessException("PDF de multa só disponível para cancelamentos com multa");
        }

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);

        Context ctx = new Context();
        ctx.setVariable("dados", pdfMapper.toReciboPdfDataMulta(orcamento, empresa));

        return renderizarPdf("pdf-multa", ctx);
    }

    public byte[] gerarReciboEstornoSinal(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (!Boolean.TRUE.equals(orcamento.getEstornoSinal())) {
            throw new BusinessException("Recibo de estorno só disponível para cancelamentos com estorno de sinal");
        }

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);

        Context ctx = new Context();
        ctx.setVariable("dados", pdfMapper.toReciboPdfDataEstorno(orcamento, empresa));

        return renderizarPdf("recibo-estorno", ctx);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
