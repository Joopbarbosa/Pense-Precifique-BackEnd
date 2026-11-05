package com.penseprecifique.api.financeiro;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** V0.19.0 (#1177, RN-NOVA-23) — categorias de despesa. Listar semeia as padrão na primeira vez. */
@RestController
@RequestMapping("/financeiro/categorias")
@RequiredArgsConstructor
public class CategoriaFinanceiraController {

    private final CategoriaFinanceiraService service;

    @GetMapping
    public ResponseEntity<List<CategoriaFinanceiraResponse>> listar(@RequestParam(defaultValue = "false") boolean incluirInativas) {
        return ResponseEntity.ok(service.listar(incluirInativas));
    }

    @PostMapping
    public ResponseEntity<CategoriaFinanceiraResponse> criar(@RequestBody CategoriaFinanceiraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request.nome()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaFinanceiraResponse> renomear(@PathVariable UUID id, @RequestBody CategoriaFinanceiraRequest request) {
        return ResponseEntity.ok(service.renomear(id, request.nome()));
    }

    @PostMapping("/{id}/inativar")
    public ResponseEntity<CategoriaFinanceiraResponse> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.inativar(id));
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<CategoriaFinanceiraResponse> reativar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reativar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
