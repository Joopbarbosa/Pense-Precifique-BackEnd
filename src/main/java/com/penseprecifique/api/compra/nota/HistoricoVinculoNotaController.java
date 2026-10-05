package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.dto.request.compra.IgnorarVinculoNotaRequest;
import com.penseprecifique.api.shared.dto.request.compra.VinculoNotaRequest;
import com.penseprecifique.api.shared.dto.response.compra.VinculoNotaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/compras/nota/vinculos")
@RequiredArgsConstructor
public class HistoricoVinculoNotaController {
    private final HistoricoVinculoNotaService service;
    @GetMapping
    public ResponseEntity<Page<VinculoNotaResponse>> listar(@RequestParam(required = false) String busca,
            @RequestParam(required = false) UUID fornecedorId, @RequestParam(required = false) String emitenteCnpj,
            @RequestParam(required = false) UUID insumoId, @RequestParam(required = false) Boolean ignorar,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listar(busca, fornecedorId, emitenteCnpj, insumoId, ignorar, pageable));
    }
    @PutMapping("/{id}")
    public ResponseEntity<VinculoNotaResponse> editar(@PathVariable UUID id, @Valid @RequestBody VinculoNotaRequest request) {
        return ResponseEntity.ok(service.editar(id, request));
    }
    @PatchMapping("/{id}/ignorar")
    public ResponseEntity<VinculoNotaResponse> ignorar(@PathVariable UUID id, @Valid @RequestBody IgnorarVinculoNotaRequest request) {
        return ResponseEntity.ok(service.ignorar(id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desfazer(@PathVariable UUID id) { service.desfazer(id); return ResponseEntity.noContent().build(); }
}
