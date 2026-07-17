package com.penseprecifique.api.controller;

import com.penseprecifique.api.shared.dto.request.RegistrarLoteCompraRequestDTO;
import com.penseprecifique.api.shared.dto.response.ImpactoAgregadoResponseDTO;
import com.penseprecifique.api.service.LoteCompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lotes-compra")
@RequiredArgsConstructor
public class LoteCompraController {

    private final LoteCompraService loteCompraService;

    @PostMapping
    public ResponseEntity<ImpactoAgregadoResponseDTO> registrar(
            @Valid @RequestBody RegistrarLoteCompraRequestDTO request) {
        return ResponseEntity.status(201).body(loteCompraService.registrarLote(request));
    }
}
