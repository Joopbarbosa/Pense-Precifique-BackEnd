package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.dto.request.caixa.AbrirCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.CaixaMovimentoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.FecharCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaMovimentoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaTurnoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.FechamentoPreviaResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/caixa")
@RequiredArgsConstructor
public class CaixaTurnoController {

    private final CaixaTurnoService caixaTurnoService;

    @PostMapping("/turnos")
    public ResponseEntity<CaixaTurnoResponseDTO> abrirTurno(@Valid @RequestBody AbrirCaixaTurnoRequestDTO request) {
        return ResponseEntity.ok(caixaTurnoService.abrirTurno(request));
    }

    @GetMapping("/turnos/atual")
    public ResponseEntity<CaixaTurnoResponseDTO> buscarTurnoAberto() {
        return ResponseEntity.ok(caixaTurnoService.buscarTurnoAberto());
    }

    /** #488 (V0.12.0) — valor esperado antes de fechar, para a tela saber se cobra justificativa. */
    @GetMapping("/turnos/{id}/fechamento-previa")
    public ResponseEntity<FechamentoPreviaResponseDTO> previaFechamento(@PathVariable UUID id) {
        return ResponseEntity.ok(caixaTurnoService.previaFechamento(id));
    }

    @PostMapping("/turnos/{id}/fechar")
    public ResponseEntity<CaixaTurnoResponseDTO> fecharTurno(
            @PathVariable UUID id, @Valid @RequestBody FecharCaixaTurnoRequestDTO request) {
        return ResponseEntity.ok(caixaTurnoService.fecharTurno(id, request));
    }

    @GetMapping("/turnos/{id}/movimentos")
    public ResponseEntity<List<CaixaMovimentoResponseDTO>> listarMovimentos(@PathVariable UUID id) {
        return ResponseEntity.ok(caixaTurnoService.listarMovimentos(id));
    }

    @PostMapping("/movimentos")
    public ResponseEntity<CaixaMovimentoResponseDTO> registrarMovimento(
            @Valid @RequestBody CaixaMovimentoRequestDTO request) {
        return ResponseEntity.ok(caixaTurnoService.registrarMovimento(request));
    }
}
