package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.TravarProducaoRequest;
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
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) EstadoProducao estado,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(producaoService.listar(busca, estado, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProducaoDetalheResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(producaoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProducaoDetalheResponse> criar(@Valid @RequestBody CriarProducaoRequest request) {
        return ResponseEntity.status(201).body(producaoService.criarProducao(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProducaoDetalheResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody CriarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.editarProducao(id, request));
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

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<ProducaoDetalheResponse> iniciar(
            @PathVariable UUID id,
            @RequestBody(required = false) IniciarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.iniciar(id, request != null ? request : new IniciarProducaoRequest()));
    }

    @PostMapping("/{id}/travar")
    public ResponseEntity<ProducaoDetalheResponse> travar(
            @PathVariable UUID id,
            @Valid @RequestBody TravarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.travar(id, request));
    }

    @PostMapping("/{id}/retomar")
    public ResponseEntity<ProducaoDetalheResponse> retomar(@PathVariable UUID id) {
        return ResponseEntity.ok(producaoService.retomar(id));
    }
}
