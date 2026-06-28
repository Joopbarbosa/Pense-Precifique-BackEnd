package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.Empresa;
import com.penseprecifique.api.domain.entity.Orcamento;
import com.penseprecifique.api.domain.entity.OrcamentoItem;
import com.penseprecifique.api.domain.entity.ReciboPagamento;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.StatusOrcamento;
import com.penseprecifique.api.domain.enums.TipoCancelamento;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.repository.EmpresaRepository;
import com.penseprecifique.api.repository.OrcamentoItemRepository;
import com.penseprecifique.api.repository.OrcamentoRepository;
import com.penseprecifique.api.repository.ReciboPagamentoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PdfService {

    private final SpringTemplateEngine templateEngine;
    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final PdfMapper pdfMapper;

    private byte[] renderizarPdf(String template, Context ctx) {
        String html = templateEngine.process("pdf/" + template, ctx);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Usando reflexão para evitar importar classes que podem não existir
            Class<?> rendererBuilderClass = Class.forName("com.openhtmltopdf.pdfboxbase.PdfRendererBuilder");
            Object builder = rendererBuilderClass.getConstructor().newInstance();

            Method withHtmlContent = rendererBuilderClass.getMethod("withHtmlContent", String.class, String.class);
            withHtmlContent.invoke(builder, html, null);

            Method toStream = rendererBuilderClass.getMethod("toStream", ByteArrayOutputStream.class);
            toStream.invoke(builder, baos);

            Method run = rendererBuilderClass.getMethod("run");
            run.invoke(builder);

            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar PDF: " + e.getMessage());
        }
    }

    public byte[] gerarPdfOrcamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());

        Context ctx = new Context();
        ctx.setVariable("dados", pdfMapper.toOrcamentoPdfData(orcamento, empresa, itens));

        return renderizarPdf("orcamento", ctx);
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
