package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.FinalizarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.RetomarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.TravarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.producao.InsumoConsumidoResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
            @RequestParam(required = false) LocalDate dataInicioDe,
            @RequestParam(required = false) LocalDate dataInicioAte,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(producaoService.listar(busca, estado, dataInicioDe, dataInicioAte, pageable));
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

    @PostMapping("/simular-alertas")
    public ResponseEntity<List<AlertaInsumoResponse>> simularAlertas(
            @Valid @RequestBody List<ProducaoProdutoRequest> produtos) {
        return ResponseEntity.ok(producaoService.simularAlertas(produtos));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ProducaoDetalheResponse> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.cancelar(id, request));
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<Object> iniciar(
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
    public ResponseEntity<Object> retomar(
            @PathVariable UUID id,
            @RequestBody(required = false) RetomarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.retomar(id, request));
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<ProducaoDetalheResponse> finalizar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) FinalizarProducaoRequest request) {
        return ResponseEntity.ok(producaoService.finalizar(id, request));
    }

    @PostMapping("/agrupar")
    public ResponseEntity<Object> agrupar(@Valid @RequestBody AgruparProducoesRequest request) {
        return ResponseEntity.ok(producaoService.agrupar(request));
    }
}
