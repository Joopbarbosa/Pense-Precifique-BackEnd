package com.penseprecifique.api.controller;

import com.penseprecifique.api.domain.enums.TipoProduto;
import com.penseprecifique.api.dto.request.BaixaManualProdutoRequest;
import com.penseprecifique.api.dto.request.ProdutoRequest;
import com.penseprecifique.api.dto.response.MovimentacaoProdutoResponse;
import com.penseprecifique.api.dto.response.ProdutoDetalheResponse;
import com.penseprecifique.api.dto.response.ProdutoResponse;
import com.penseprecifique.api.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @GetMapping
    public ResponseEntity<Page<ProdutoResponse>> listar(
            @RequestParam(required = false) TipoProduto tipo,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(produtoService.listar(tipo, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoDetalheResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProdutoDetalheResponse> cadastrar(@Valid @RequestBody ProdutoRequest request) {
        return ResponseEntity.status(201).body(produtoService.cadastrar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoDetalheResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody ProdutoRequest request) {
        return ResponseEntity.ok(produtoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        produtoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/movimentacoes")
    public ResponseEntity<Page<MovimentacaoProdutoResponse>> listarMovimentacoes(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(produtoService.listarMovimentacoes(id, pageable));
    }

    @PostMapping("/{id}/baixa-manual")
    public ResponseEntity<MovimentacaoProdutoResponse> baixaManual(
            @PathVariable UUID id,
            @Valid @RequestBody BaixaManualProdutoRequest request) {
        return ResponseEntity.status(201).body(produtoService.baixaManual(id, request));
    }
}
