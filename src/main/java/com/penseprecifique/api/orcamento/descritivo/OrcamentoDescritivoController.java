package com.penseprecifique.api.orcamento.descritivo;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.request.orcamento.DescritivoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.DescritivoStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.GerarVendaDescritivoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.DescritivoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.DescritivoResumoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * V0.19.0 #382 (RN-NOVA-44, UC-NOVO-5) — orçamento Descritivo. O PDF e o preview usam os mesmos endpoints do
 * orçamento ({@code /orcamentos/{id}/pdf} e {@code /preview-html}), que escolhem o payload pelo tipo.
 */
@RestController
@RequestMapping("/orcamentos-descritivos")
@RequiredArgsConstructor
public class OrcamentoDescritivoController {

    private final OrcamentoDescritivoService service;

    @GetMapping
    public ResponseEntity<Page<DescritivoResumoResponse>> listar(
            @RequestParam(required = false) StatusOrcamento status,
            @RequestParam(required = false) @Size(max = 200, message = "Máximo de 200 caracteres") String busca,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listar(status, busca, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DescritivoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PostMapping
    public ResponseEntity<DescritivoResponse> criar(@Valid @RequestBody DescritivoRequest request) {
        return ResponseEntity.status(201).body(service.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DescritivoResponse> editar(@PathVariable UUID id, @Valid @RequestBody DescritivoRequest request) {
        return ResponseEntity.ok(service.editar(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<DescritivoResponse> mudarStatus(@PathVariable UUID id,
                                                          @Valid @RequestBody DescritivoStatusRequest request) {
        return ResponseEntity.ok(service.mudarStatus(id, request));
    }

    @PostMapping("/{id}/gerar-venda")
    public ResponseEntity<OrcamentoDetalheResponse> gerarVenda(@PathVariable UUID id,
                                                               @Valid @RequestBody GerarVendaDescritivoRequest request) {
        return ResponseEntity.status(201).body(service.gerarVenda(id, request));
    }
}
