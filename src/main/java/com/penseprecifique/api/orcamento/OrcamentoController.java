package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.CriarProducaoVinculadaRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.SimularAlertasOrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.ItemSemEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoProducaoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.SimulacaoAvancoStatusResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.SimulacaoEstoqueProdutoResponse;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.pdf.PdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orcamentos")
@RequiredArgsConstructor
public class OrcamentoController {

    private final OrcamentoService orcamentoService;
    private final PdfService pdfService;
    private final ItemCatalogoService itemCatalogoService;

    @GetMapping
    public ResponseEntity<Page<OrcamentoResponse>> listar(
            @RequestParam(required = false) StatusOrcamento status,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) LocalDate dataCriacaoDe,
            @RequestParam(required = false) LocalDate dataCriacaoAte,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orcamentoService.listar(status, busca, dataCriacaoDe, dataCriacaoAte, pageable));
    }

    /**
     * P-B008/#353 — {@code Pageable} passado ao Service/Repository para limitar o tamanho da
     * consulta (tech debt, base de itens de catálogo crescendo sem bound); contrato HTTP
     * inalterado — resposta continua array simples (não expõe metadados de paginação), decisão
     * confirmada no Passo 0 para não quebrar o consumidor atual (`ItemSearch`/`orcamentoService.ts`,
     * que espera `Promise<ItemCatalogoBuscaResponse[]>`), já que não há tarefa de Frontend neste
     * pocket para ajustar esse contrato.
     */
    @GetMapping("/itens-catalogo")
    public ResponseEntity<List<ItemCatalogoBuscaResponse>> buscarItensCatalogo(
            @RequestParam(required = false) UUID catalogoId,
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 8) Pageable pageable) {
        return ResponseEntity.ok(itemCatalogoService.buscarParaOrcamento(catalogoId, busca, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrcamentoDetalheResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(orcamentoService.buscarPorId(id));
    }

    @GetMapping("/{id}/itens-sem-estoque")
    public ResponseEntity<List<ItemSemEstoqueResponse>> itensSemEstoque(@PathVariable UUID id) {
        return ResponseEntity.ok(orcamentoService.itensSemEstoque(id));
    }

    @PostMapping
    public ResponseEntity<OrcamentoDetalheResponse> criar(
            @Valid @RequestBody OrcamentoRequest request) {
        return ResponseEntity.status(201).body(orcamentoService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrcamentoDetalheResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody OrcamentoRequest request) {
        return ResponseEntity.ok(orcamentoService.editar(id, request));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<OrcamentoDetalheResponse> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(orcamentoService.duplicar(id));
    }

    @PostMapping("/simular-alertas")
    public ResponseEntity<List<SimulacaoEstoqueProdutoResponse>> simularAlertas(
            @RequestBody List<SimularAlertasOrcamentoItemRequest> itens) {
        return ResponseEntity.ok(orcamentoService.simularAlertas(itens));
    }

    @PostMapping("/{id}/vincular-producao")
    public ResponseEntity<List<OrcamentoProducaoResponse>> vincularProducao(
            @PathVariable UUID id,
            @Valid @RequestBody VincularProducaoRequest request) {
        return ResponseEntity.status(201).body(orcamentoService.vincularProducao(id, request));
    }

    @PostMapping("/{id}/simular-vincular-producao")
    public ResponseEntity<List<AlertaInsumoResponse>> simularVincularProducao(
            @PathVariable UUID id,
            @Valid @RequestBody VincularProducaoRequest request) {
        return ResponseEntity.ok(orcamentoService.simularVincularProducao(id, request));
    }

    @DeleteMapping("/{id}/vincular-producao/{producaoId}")
    public ResponseEntity<Void> desvincularProducao(
            @PathVariable UUID id,
            @PathVariable UUID producaoId,
            @RequestParam(required = false, defaultValue = "false") boolean manterProdutos) {
        orcamentoService.desvincularProducao(id, producaoId, manterProdutos);
        return ResponseEntity.noContent().build();
    }

    // RN-NOVA-17 (V0.8.3, #375+308) — "Não, remover": remove a contribuição de 1 produto numa
    // produção já EM_ANDAMENTO/TRAVADA, sem reverter estoque. Não remove o vínculo em si — ver
    // desvincularProducao(..., manterProdutos=true) para fechar o vínculo depois de resolvidas
    // todas as perguntas "por produto".
    @DeleteMapping("/{id}/vincular-producao/{producaoId}/produtos/{produtoId}")
    public ResponseEntity<Void> removerProdutoDeProducaoAtiva(
            @PathVariable UUID id,
            @PathVariable UUID producaoId,
            @PathVariable UUID produtoId) {
        orcamentoService.removerProdutoDeProducaoAtiva(id, producaoId, produtoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/criar-producao-vinculada")
    public ResponseEntity<List<OrcamentoProducaoResponse>> criarProducaoVinculada(
            @PathVariable UUID id,
            @Valid @RequestBody CriarProducaoVinculadaRequest request) {
        return ResponseEntity.status(201).body(orcamentoService.criarProducaoVinculada(id, request));
    }

    @PostMapping("/{id}/avancar-status")
    public ResponseEntity<Object> avancarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AvancaStatusRequest request) {
        return ResponseEntity.ok(orcamentoService.avancarStatus(id,
                request != null ? request : new AvancaStatusRequest()));
    }

    @PostMapping("/{id}/simular-avancar-status")
    public ResponseEntity<SimulacaoAvancoStatusResponse> simularAvancarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AvancaStatusRequest request) {
        return ResponseEntity.ok(orcamentoService.simularAvancarStatus(id,
                request != null ? request : new AvancaStatusRequest()));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<OrcamentoDetalheResponse> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AvancaStatusRequest request) {
        return ResponseEntity.ok(orcamentoService.cancelar(id,
                request != null ? request : new AvancaStatusRequest()));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarPdfOrcamento(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=orcamento.pdf")
                .body(pdf);
    }

    @GetMapping(value = "/{id}/preview-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewHtml(@PathVariable UUID id) {
        String html = pdfService.gerarPreviewHtmlOrcamento(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping("/{id}/recibo-sinal")
    public ResponseEntity<byte[]> downloadReciboSinal(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarReciboSinal(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=recibo-sinal.pdf")
                .body(pdf);
    }

    @GetMapping(value = "/{id}/recibo-sinal/preview-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewHtmlReciboSinal(@PathVariable UUID id) {
        String html = pdfService.gerarPreviewHtmlReciboSinal(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping("/{id}/pdf-multa")
    public ResponseEntity<byte[]> downloadPdfMulta(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarPdfMulta(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=multa.pdf")
                .body(pdf);
    }

    @GetMapping(value = "/{id}/pdf-multa/preview-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewHtmlPdfMulta(@PathVariable UUID id) {
        String html = pdfService.gerarPreviewHtmlPdfMulta(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping("/{id}/recibo-estorno")
    public ResponseEntity<byte[]> downloadReciboEstorno(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarReciboEstornoSinal(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=recibo-estorno.pdf")
                .body(pdf);
    }

    @GetMapping(value = "/{id}/recibo-estorno/preview-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewHtmlReciboEstorno(@PathVariable UUID id) {
        String html = pdfService.gerarPreviewHtmlReciboEstornoSinal(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping("/{id}/recibo-pagamento")
    public ResponseEntity<byte[]> downloadReciboPagamento(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarReciboPagamento(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=recibo-pagamento.pdf")
                .body(pdf);
    }

    @GetMapping(value = "/{id}/recibo-pagamento/preview-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewHtmlReciboPagamento(@PathVariable UUID id) {
        String html = pdfService.gerarPreviewHtmlReciboPagamento(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
