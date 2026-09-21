package com.penseprecifique.api.unidademedida;

import com.penseprecifique.api.shared.dto.request.unidademedida.UnidadeMedidaRequestDTO;
import com.penseprecifique.api.shared.dto.response.unidademedida.UnidadeMedidaResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/unidades-medida")
@RequiredArgsConstructor
public class UnidadeMedidaController {

    private final UnidadeMedidaService unidadeMedidaService;

    @GetMapping
    public ResponseEntity<List<UnidadeMedidaResponseDTO>> listar() {
        return ResponseEntity.ok(unidadeMedidaService.listar());
    }

    @PostMapping
    public ResponseEntity<UnidadeMedidaResponseDTO> cadastrar(@Valid @RequestBody UnidadeMedidaRequestDTO request) {
        return ResponseEntity.status(201).body(unidadeMedidaService.cadastrar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnidadeMedidaResponseDTO> editar(
            @PathVariable UUID id, @Valid @RequestBody UnidadeMedidaRequestDTO request) {
        return ResponseEntity.ok(unidadeMedidaService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        unidadeMedidaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
