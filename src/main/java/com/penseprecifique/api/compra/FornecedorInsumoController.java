package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.dto.request.compra.FornecedorInsumoRequest;
import com.penseprecifique.api.shared.dto.request.compra.PrecoReferenciaRequest;
import com.penseprecifique.api.shared.dto.response.compra.FornecedorInsumoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** #540/RN-NOVA-6 (V0.15.0) — vínculos Fornecedor↔Insumo. */
@RestController
@RequestMapping("/fornecedor-insumos")
@RequiredArgsConstructor
public class FornecedorInsumoController {

    private final FornecedorInsumoService fornecedorInsumoService;

    /** Exatamente um dos dois filtros: os insumos de um fornecedor, ou os fornecedores de um insumo. */
    @GetMapping
    public ResponseEntity<List<FornecedorInsumoResponse>> listar(
            @RequestParam(required = false) UUID fornecedorId,
            @RequestParam(required = false) UUID insumoId) {
        return ResponseEntity.ok(fornecedorInsumoService.listar(fornecedorId, insumoId));
    }

    @PostMapping
    public ResponseEntity<FornecedorInsumoResponse> criar(@Valid @RequestBody FornecedorInsumoRequest request) {
        return ResponseEntity.status(201).body(fornecedorInsumoService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FornecedorInsumoResponse> atualizarPreco(
            @PathVariable UUID id, @Valid @RequestBody PrecoReferenciaRequest request) {
        return ResponseEntity.ok(fornecedorInsumoService.atualizarPreco(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        fornecedorInsumoService.remover(id);
        return ResponseEntity.noContent().build();
    }
}
