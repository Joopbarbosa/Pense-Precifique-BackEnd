package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.BaixaManualProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.MovimentacaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.produto.PrecoSugeridoResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoContagensResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @GetMapping
    public ResponseEntity<Page<ProdutoResponse>> listar(
            @RequestParam(required = false) TipoProduto tipo,
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(produtoService.listar(tipo, busca, pageable));
    }

    @GetMapping("/contagens")
    public ResponseEntity<ProdutoContagensResponse> contagens() {
        return ResponseEntity.ok(produtoService.contagens());
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
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        produtoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable UUID id) {
        produtoService.reativar(id);
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

    @GetMapping("/{id}/preco-sugerido")
    public ResponseEntity<PrecoSugeridoResponse> precoSugerido(
            @PathVariable UUID id,
            @RequestParam BigDecimal margem) {
        return ResponseEntity.ok(produtoService.calcularPrecoSugeridoAvulso(id, margem));
    }
}
