package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoPreviewRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoPrecoSugeridoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/catalogos/{catalogoId}/itens")
@RequiredArgsConstructor
public class ItemCatalogoController {

    private final ItemCatalogoService itemCatalogoService;

    @GetMapping
    public ResponseEntity<List<ItemCatalogoResponse>> listar(@PathVariable UUID catalogoId) {
        return ResponseEntity.ok(itemCatalogoService.listarPorCatalogo(catalogoId));
    }

    @PostMapping
    public ResponseEntity<ItemCatalogoResponse> adicionar(
            @PathVariable UUID catalogoId,
            @Valid @RequestBody ItemCatalogoRequest request) {
        return ResponseEntity.status(201).body(itemCatalogoService.adicionar(catalogoId, request));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ItemCatalogoResponse> editar(
            @PathVariable UUID catalogoId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemCatalogoRequest request) {
        return ResponseEntity.ok(itemCatalogoService.editar(itemId, request));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remover(
            @PathVariable UUID catalogoId,
            @PathVariable UUID itemId) {
        itemCatalogoService.remover(itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview-preco")
    public ResponseEntity<ItemCatalogoPrecoSugeridoResponse> previewPreco(
            @PathVariable UUID catalogoId,
            @Valid @RequestBody ItemCatalogoPreviewRequest request) {
        return ResponseEntity.ok(itemCatalogoService.previewPreco(catalogoId, request));
    }
}
