package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.dto.request.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.DuplicarCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.CatalogoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping
    public ResponseEntity<Page<CatalogoResponse>> listar(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogoService.listar(busca, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CatalogoResponse> cadastrar(@Valid @RequestBody CatalogoRequest request) {
        return ResponseEntity.status(201).body(catalogoService.cadastrar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatalogoResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody CatalogoRequest request) {
        return ResponseEntity.ok(catalogoService.editar(id, request));
    }

    @PostMapping("/{id}/desativar")
    public ResponseEntity<CatalogoResponse> desativar(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.desativar(id));
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<CatalogoResponse> reativar(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.reativar(id));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<CatalogoResponse> duplicar(
            @PathVariable UUID id,
            @RequestBody(required = false) DuplicarCatalogoRequest request) {
        return ResponseEntity.status(201).body(catalogoService.duplicar(id, request));
    }
}
