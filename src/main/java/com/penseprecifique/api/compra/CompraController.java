package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraConfirmacaoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.SimulacaoCancelamentoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.DashboardComprasResponse;
import com.penseprecifique.api.shared.dto.response.compra.EvolucaoPrecoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** #541/#550 (V0.15.0) — Compras: listagem, rascunho, confirmação e pagamento. */
@RestController
@RequestMapping("/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;
    private final DashboardCompraService dashboardCompraService;

    @GetMapping
    public ResponseEntity<Page<CompraResumoResponse>> listar(
            @RequestParam(required = false) StatusCompra status,
            @RequestParam(required = false) UUID fornecedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @PageableDefault(size = 20, sort = {"dataCompra", "numero"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(compraService.listar(status, fornecedorId, de, ate, pageable));
    }

    /** #548/RN-NOVA-15 — cards do dashboard (só CONFIRMADAS). */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardComprasResponse> dashboard() {
        return ResponseEntity.ok(dashboardCompraService.dashboard());
    }

    /** #548 — evolução do preço pago, até 5 insumos; período default = últimos 3 meses. */
    @GetMapping("/evolucao-preco")
    public ResponseEntity<EvolucaoPrecoResponse> evolucaoPreco(
            @RequestParam(required = false) List<UUID> insumoIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(dashboardCompraService.evolucaoPreco(insumoIds, de, ate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompraResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(compraService.buscar(id));
    }

    /** "Salvar rascunho" de compra nova — o COM-N nasce aqui. */
    @PostMapping
    public ResponseEntity<CompraResponse> criarRascunho(@Valid @RequestBody CompraRequest request) {
        return ResponseEntity.status(201).body(compraService.criarRascunho(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompraResponse> atualizarRascunho(@PathVariable UUID id, @Valid @RequestBody CompraRequest request) {
        return ResponseEntity.ok(compraService.atualizarRascunho(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirRascunho(@PathVariable UUID id) {
        compraService.excluirRascunho(id);
        return ResponseEntity.noContent().build();
    }

    /** Confirmar compra nova sem rascunho prévio (tudo ou nada: se falhar, nada é criado). */
    @PostMapping("/confirmar")
    public ResponseEntity<CompraConfirmacaoResponse> confirmarNova(@Valid @RequestBody CompraRequest request) {
        return ResponseEntity.status(201).body(compraService.confirmarNova(request));
    }

    /** Confirmar rascunho; o corpo (opcional) grava as alterações antes, na mesma transação. */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<CompraConfirmacaoResponse> confirmar(@PathVariable UUID id,
                                                    @Valid @RequestBody(required = false) CompraRequest request) {
        return ResponseEntity.ok(compraService.confirmar(id, request));
    }

    /** #544 — prévia do cancelamento: bloqueios (estoque negativo) e avisos (custo mantido). */
    @PostMapping("/{id}/simular-cancelamento")
    public ResponseEntity<SimulacaoCancelamentoResponse> simularCancelamento(@PathVariable UUID id) {
        return ResponseEntity.ok(compraService.simularCancelamento(id));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<CompraConfirmacaoResponse> cancelar(@PathVariable UUID id,
                                                              @Valid @RequestBody CancelarCompraRequest request) {
        return ResponseEntity.ok(compraService.cancelar(id, request));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<CompraResponse> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(compraService.duplicar(id));
    }

    @PatchMapping("/{id}/pagamento")
    public ResponseEntity<CompraResponse> atualizarPagamento(@PathVariable UUID id,
                                                             @Valid @RequestBody PagamentoCompraRequest request) {
        return ResponseEntity.ok(compraService.atualizarPagamento(id, request));
    }
}
