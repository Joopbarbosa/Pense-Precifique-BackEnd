package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.LancarProducaoLoteRequest;
import com.penseprecifique.api.shared.dto.request.LancarProducaoRequest;
import com.penseprecifique.api.shared.dto.response.InsumoConsumidoResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/producoes")
@RequiredArgsConstructor
public class ProducaoController {

    private final ProducaoService producaoService;

    @GetMapping
    public ResponseEntity<Page<ProducaoResponse>> listar(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(producaoService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProducaoDetalheResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(producaoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProducaoDetalheResponse> lancar(@Valid @RequestBody LancarProducaoRequest request) {
        return ResponseEntity.status(201).body(producaoService.lancar(request));
    }

    @PostMapping("/lote")
    public ResponseEntity<List<ProducaoDetalheResponse>> lancarLote(
            @Valid @RequestBody LancarProducaoLoteRequest request) {
        return ResponseEntity.status(201).body(producaoService.lancarLote(request));
    }

    @GetMapping("/preview")
    public ResponseEntity<List<InsumoConsumidoResponse>> preview(
            @RequestParam UUID produtoId,
            @RequestParam BigDecimal quantidade) {
        return ResponseEntity.ok(producaoService.previewInsumosConsumidos(produtoId, quantidade));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ProducaoDetalheResponse> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.cancelar(id, request));
    }
}
