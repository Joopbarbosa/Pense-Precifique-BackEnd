package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.dto.request.compra.GerarListaCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.PreviaListaCompraResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** #546/RN-NOVA-12/13 (V0.15.0) — Lista de Compras: prévia, gerar (LST-N), histórico, criar compra. */
@RestController
@RequestMapping("/listas-compra")
@RequiredArgsConstructor
public class ListaCompraController {

    private final ListaCompraService listaCompraService;

    /** Prévia calculada na hora, não salva (DT-NOVA-8). {@code insumoIds} = seleção manual. */
    @GetMapping("/previa")
    public ResponseEntity<PreviaListaCompraResponse> previa(
            @RequestParam(defaultValue = "false") boolean abaixoMinimo,
            @RequestParam(defaultValue = "false") boolean estoqueNegativo,
            @RequestParam(required = false) UUID fornecedorId,
            @RequestParam(required = false) List<UUID> insumoIds) {
        return ResponseEntity.ok(listaCompraService.previa(abaixoMinimo, estoqueNegativo, fornecedorId, insumoIds));
    }

    @PostMapping
    public ResponseEntity<ListaCompraResponse> gerar(@Valid @RequestBody GerarListaCompraRequest request) {
        return ResponseEntity.status(201).body(listaCompraService.gerar(request));
    }

    @GetMapping
    public ResponseEntity<Page<ListaCompraResumoResponse>> historico(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(listaCompraService.historico(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListaCompraResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(listaCompraService.buscar(id));
    }

    @PostMapping("/{id}/criar-compra")
    public ResponseEntity<CompraResponse> criarCompra(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(listaCompraService.criarCompra(id));
    }
}
