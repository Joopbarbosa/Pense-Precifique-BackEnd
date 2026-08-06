package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.EmpresaResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empresa")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<EmpresaResponseDTO> getEmpresa() {
        return ResponseEntity.ok(empresaService.getEmpresa());
    }

    @PutMapping
    public ResponseEntity<EmpresaResponseDTO> upsertEmpresa(@Valid @RequestBody EmpresaRequestDTO request) {
        return ResponseEntity.ok(empresaService.upsertEmpresa(request));
    }
}
