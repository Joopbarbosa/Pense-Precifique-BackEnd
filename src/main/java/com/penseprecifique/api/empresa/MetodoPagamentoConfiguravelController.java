package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelRequestDTO;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelUpdateRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.MetodoPagamentoConfiguravelResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/configuracoes/metodos-pagamento")
@RequiredArgsConstructor
public class MetodoPagamentoConfiguravelController {

    private final MetodoPagamentoConfiguravelService metodoPagamentoService;

    @GetMapping
    public ResponseEntity<List<MetodoPagamentoConfiguravelResponseDTO>> listar() {
        return ResponseEntity.ok(metodoPagamentoService.listar());
    }

    @PostMapping
    public ResponseEntity<MetodoPagamentoConfiguravelResponseDTO> criar(
            @Valid @RequestBody MetodoPagamentoConfiguravelRequestDTO request) {
        return ResponseEntity.ok(metodoPagamentoService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MetodoPagamentoConfiguravelResponseDTO> atualizar(
            @PathVariable UUID id, @Valid @RequestBody MetodoPagamentoConfiguravelUpdateRequestDTO request) {
        return ResponseEntity.ok(metodoPagamentoService.atualizar(id, request));
    }
}
