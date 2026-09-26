package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
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
import java.util.UUID;

/** #541/#550 (V0.15.0) — Compras: listagem, rascunho, confirmação e pagamento. */
@RestController
@RequestMapping("/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @GetMapping
    public ResponseEntity<Page<CompraResumoResponse>> listar(
            @RequestParam(required = false) StatusCompra status,
            @RequestParam(required = false) UUID fornecedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @PageableDefault(size = 20, sort = {"dataCompra", "numero"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(compraService.listar(status, fornecedorId, de, ate, pageable));
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
    public ResponseEntity<CompraResponse> confirmarNova(@Valid @RequestBody CompraRequest request) {
        return ResponseEntity.status(201).body(compraService.confirmarNova(request));
    }

    /** Confirmar rascunho; o corpo (opcional) grava as alterações antes, na mesma transação. */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<CompraResponse> confirmar(@PathVariable UUID id,
                                                    @Valid @RequestBody(required = false) CompraRequest request) {
        return ResponseEntity.ok(compraService.confirmar(id, request));
    }

    @PatchMapping("/{id}/pagamento")
    public ResponseEntity<CompraResponse> atualizarPagamento(@PathVariable UUID id,
                                                             @Valid @RequestBody PagamentoCompraRequest request) {
        return ResponseEntity.ok(compraService.atualizarPagamento(id, request));
    }
}
