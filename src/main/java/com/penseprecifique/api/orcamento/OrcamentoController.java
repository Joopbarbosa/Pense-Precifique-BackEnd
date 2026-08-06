package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.SimularAlertasOrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.ItemSemEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoResponse;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.pdf.PdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/itens-catalogo")
    public ResponseEntity<List<ItemCatalogoBuscaResponse>> buscarItensCatalogo(
            @RequestParam(required = false) UUID catalogoId) {
        return ResponseEntity.ok(itemCatalogoService.buscarParaOrcamento(catalogoId));
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

    @PostMapping("/simular-alertas")
    public ResponseEntity<List<AlertaInsumoResponse>> simularAlertas(
            @RequestBody List<SimularAlertasOrcamentoItemRequest> itens) {
        return ResponseEntity.ok(orcamentoService.simularAlertas(itens));
    }

    @PostMapping("/{id}/avancar-status")
    public ResponseEntity<Object> avancarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AvancaStatusRequest request) {
        return ResponseEntity.ok(orcamentoService.avancarStatus(id,
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

    @GetMapping("/{id}/recibo-sinal")
    public ResponseEntity<byte[]> downloadReciboSinal(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarReciboSinal(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=recibo-sinal.pdf")
                .body(pdf);
    }

    @GetMapping("/{id}/pdf-multa")
    public ResponseEntity<byte[]> downloadPdfMulta(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarPdfMulta(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=multa.pdf")
                .body(pdf);
    }

    @GetMapping("/{id}/recibo-estorno")
    public ResponseEntity<byte[]> downloadReciboEstorno(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarReciboEstornoSinal(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=recibo-estorno.pdf")
                .body(pdf);
    }

    @GetMapping("/{id}/recibo-pagamento")
    public ResponseEntity<byte[]> downloadReciboPagamento(@PathVariable UUID id) {
        byte[] pdf = pdfService.gerarReciboPagamento(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=recibo-pagamento.pdf")
                .body(pdf);
    }
}
