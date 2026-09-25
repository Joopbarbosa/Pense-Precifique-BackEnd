package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.EmpresaResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /** #532 (DT-NOVA-5) — multipart, não presigned URL (validação de formato/tamanho fica no Backend). */
    @PostMapping("/logo")
    public ResponseEntity<EmpresaResponseDTO> uploadLogo(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(empresaService.uploadLogo(arquivo));
    }

    @DeleteMapping("/logo")
    public ResponseEntity<EmpresaResponseDTO> removerLogo() {
        return ResponseEntity.ok(empresaService.removerLogo());
    }
}
