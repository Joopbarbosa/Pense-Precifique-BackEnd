package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCatalogoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCompraPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoListaComprasPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoPdfMultaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboEstornoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboPagamentoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboSinalPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final PdfMicroservicoClient pdfMicroservicoClient;
    private final OrcamentoPdfPayloadService orcamentoPdfPayloadService;
    private final ReciboPdfPayloadService reciboPdfPayloadService;
    private final ReciboPagamentoPdfPayloadService reciboPagamentoPdfPayloadService;
    private final CatalogoPdfPayloadService catalogoPdfPayloadService;
    private final ComprasPdfPayloadService comprasPdfPayloadService;

    /**
     * Delega ao microsserviço pense-precifique-pdf (fluxo D do PRD). Epic #248 completa (5/5
     * documentos no microsserviço) — fluxo Thymeleaf/OpenHTMLToPDF local removido nesta limpeza
     * (V0.8.1, PB005).
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
     * fora de qualquer {@code @Transactional}.
     */
    public byte[] gerarReciboSinal(UUID orcamentoId) {
        PdfMicroservicoReciboSinalPayload payload = reciboPdfPayloadService.montarPayloadReciboSinal(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("recibo-sinal", orcamentoId, payload);
    }

    /** Preview (Fluxo E do PRD) do recibo-sinal — mesmo padrão de {@link #gerarPreviewHtmlOrcamento}. */
    public String gerarPreviewHtmlReciboSinal(UUID orcamentoId) {
        PdfMicroservicoReciboSinalPayload payload = reciboPdfPayloadService.montarPayloadReciboSinal(orcamentoId);
        return pdfMicroservicoClient.gerarHtml("recibo-sinal", orcamentoId, payload);
    }

    /**
     * Migrado ao microsserviço em #248 — mesmo padrão de {@link #gerarReciboSinal}, mas com bean
     * colaborador próprio ({@link ReciboPagamentoPdfPayloadService}), não
     * {@link ReciboPdfPayloadService} — leitura estruturalmente diferente (entidade
     * {@code ReciboPagamento} própria, relação 1:1 com {@code Orcamento}), ver decisoes-pdf.md.
     * Último dos 5 tipos de documento a migrar — fechou a Epic #248 (4/4).
     */
    public byte[] gerarReciboPagamento(UUID orcamentoId) {
        PdfMicroservicoReciboPagamentoPayload payload =
                reciboPagamentoPdfPayloadService.montarPayloadReciboPagamento(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("recibo-pagamento", orcamentoId, payload);
    }

    /** Preview (Fluxo E do PRD) do recibo-pagamento — mesmo padrão de {@link #gerarPreviewHtmlOrcamento}. */
    public String gerarPreviewHtmlReciboPagamento(UUID orcamentoId) {
        PdfMicroservicoReciboPagamentoPayload payload =
                reciboPagamentoPdfPayloadService.montarPayloadReciboPagamento(orcamentoId);
        return pdfMicroservicoClient.gerarHtml("recibo-pagamento", orcamentoId, payload);
    }

    /** Migrado ao microsserviço em #248 (Frente A) — mesmo padrão de {@link #gerarReciboSinal}. */
    public byte[] gerarPdfMulta(UUID orcamentoId) {
        PdfMicroservicoPdfMultaPayload payload = reciboPdfPayloadService.montarPayloadPdfMulta(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("pdf-multa", orcamentoId, payload);
    }

    /** Preview (Fluxo E do PRD) do pdf-multa — mesmo padrão de {@link #gerarPreviewHtmlOrcamento}. */
    public String gerarPreviewHtmlPdfMulta(UUID orcamentoId) {
        PdfMicroservicoPdfMultaPayload payload = reciboPdfPayloadService.montarPayloadPdfMulta(orcamentoId);
        return pdfMicroservicoClient.gerarHtml("pdf-multa", orcamentoId, payload);
    }

    /** Migrado ao microsserviço em #248 (Frente A) — mesmo padrão de {@link #gerarReciboSinal}. */
    public byte[] gerarReciboEstornoSinal(UUID orcamentoId) {
        PdfMicroservicoReciboEstornoPayload payload = reciboPdfPayloadService.montarPayloadReciboEstorno(orcamentoId);
        return pdfMicroservicoClient.gerarPdf("recibo-estorno", orcamentoId, payload);
    }

    /** Preview (Fluxo E do PRD) do recibo-estorno — mesmo padrão de {@link #gerarPreviewHtmlOrcamento}. */
    public String gerarPreviewHtmlReciboEstornoSinal(UUID orcamentoId) {
        PdfMicroservicoReciboEstornoPayload payload = reciboPdfPayloadService.montarPayloadReciboEstorno(orcamentoId);
        return pdfMicroservicoClient.gerarHtml("recibo-estorno", orcamentoId, payload);
    }

    /**
     * OpenProject #519 (RN-NOVA-8) — 6º tipo de documento no PdfMapper Pattern, 1º que não deriva
     * de Orçamento. Mesmo padrão dos demais (#262): leitura de banco em
     * {@link CatalogoPdfPayloadService}, bean injetado, chamada HTTP fora de qualquer
     * {@code @Transactional}.
     */
    public byte[] gerarPdfCatalogo(UUID catalogoId) {
        PdfMicroservicoCatalogoPayload payload = catalogoPdfPayloadService.montarPayloadCatalogo(catalogoId);
        return pdfMicroservicoClient.gerarPdf("catalogo", catalogoId, payload);
    }

    /** #545/RN-NOVA-11 (V0.15.0) — PDF da compra; mesmo padrão dos demais (#262). */
    public byte[] gerarPdfCompra(UUID compraId) {
        PdfMicroservicoCompraPayload payload = comprasPdfPayloadService.montarPayloadCompra(compraId);
        return pdfMicroservicoClient.gerarPdf("compra", compraId, payload);
    }

    /** #545 — preview HTML da compra (Fluxo E do PRD). */
    public String gerarPreviewHtmlCompra(UUID compraId) {
        PdfMicroservicoCompraPayload payload = comprasPdfPayloadService.montarPayloadCompra(compraId);
        return pdfMicroservicoClient.gerarHtml("compra", compraId, payload);
    }

    /** #547/RN-NOVA-14 (V0.15.0) — PDF da lista de compras, do retrato LST-N. */
    public byte[] gerarPdfListaCompras(UUID listaId) {
        PdfMicroservicoListaComprasPayload payload = comprasPdfPayloadService.montarPayloadListaCompras(listaId);
        return pdfMicroservicoClient.gerarPdf("lista-compras", listaId, payload);
    }

    /** #547 — preview HTML da lista de compras. */
    public String gerarPreviewHtmlListaCompras(UUID listaId) {
        PdfMicroservicoListaComprasPayload payload = comprasPdfPayloadService.montarPayloadListaCompras(listaId);
        return pdfMicroservicoClient.gerarHtml("lista-compras", listaId, payload);
    }
}
