package com.penseprecifique.api.controller;

import com.penseprecifique.api.shared.dto.request.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.response.ConfiguracaoResponseDTO;
import com.penseprecifique.api.service.ConfiguracaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/configuracoes/precificacao")
@RequiredArgsConstructor
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;

    @GetMapping
    public ResponseEntity<ConfiguracaoResponseDTO> getConfiguracao() {
        return ResponseEntity.ok(configuracaoService.getConfiguracao());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoResponseDTO> upsertConfiguracao(@Valid @RequestBody ConfiguracaoRequestDTO request) {
        return ResponseEntity.ok(configuracaoService.upsertConfiguracao(request));
    }
}
