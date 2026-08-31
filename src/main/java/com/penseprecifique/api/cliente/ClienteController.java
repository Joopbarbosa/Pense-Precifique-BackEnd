package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    // #356 — padronizado para 'busca' (mesmo nome usado por Produto/Insumo/Orçamento/Catálogo/Produção);
    // era 'nome' até V0.8.2, único endpoint divergente do padrão do resto do sistema.
    @GetMapping
    public ResponseEntity<Page<ClienteResponse>> listar(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(busca, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> cadastrar(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(201).body(clienteService.cadastrar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        clienteService.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
