package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.dto.response.agenda.AgendaResumoResponse;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.penseprecifique.api.shared.dto.request.agenda.CancelarCompromissoRequest;
import com.penseprecifique.api.shared.dto.request.agenda.CompromissoUsuariaRequest;
import com.penseprecifique.api.shared.dto.request.agenda.EditarCompromissoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoUsuariaDetalheResponse;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

/** V0.18.0 (DT-NOVA-2) — Agenda: compromissos do período, faixa de atrasados e de hoje, lista de atrasados. */
@RestController
@RequestMapping("/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;
    private final CompromissoUsuariaService compromissoUsuariaService;

    @GetMapping("/compromissos")
    public ResponseEntity<List<CompromissoResponse>> compromissos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(agendaService.compromissos(de, ate));
    }

    @GetMapping("/resumo")
    public ResponseEntity<AgendaResumoResponse> resumo() {
        return ResponseEntity.ok(agendaService.resumo());
    }

    @GetMapping("/atrasados")
    public ResponseEntity<Page<CompromissoResponse>> atrasados(
            @PageableDefault(size = AgendaService.TAMANHO_PAGINA_PADRAO) Pageable pageable) {
        return ResponseEntity.ok(agendaService.atrasados(pageable));
    }

    // ---- compromisso da usuária (#1039) ------------------------------------------------------------

    @PostMapping("/compromissos-usuaria")
    public ResponseEntity<CompromissoUsuariaDetalheResponse> criarCompromisso(@Valid @RequestBody CompromissoUsuariaRequest request) {
        return ResponseEntity.status(201).body(compromissoUsuariaService.criar(request));
    }

    @GetMapping("/compromissos-usuaria/{id}/ocorrencias/{dataOriginal}")
    public ResponseEntity<CompromissoUsuariaDetalheResponse> detalheCompromisso(
            @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataOriginal) {
        return ResponseEntity.ok(compromissoUsuariaService.detalhe(id, dataOriginal));
    }

    @PostMapping("/compromissos-usuaria/{id}/ocorrencias/{dataOriginal}/concluir")
    public ResponseEntity<CompromissoUsuariaDetalheResponse> concluirCompromisso(
            @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataOriginal) {
        return ResponseEntity.ok(compromissoUsuariaService.concluir(id, dataOriginal));
    }

    @PostMapping("/compromissos-usuaria/{id}/ocorrencias/{dataOriginal}/cancelar")
    public ResponseEntity<CompromissoUsuariaDetalheResponse> cancelarCompromisso(
            @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataOriginal,
            @Valid @RequestBody CancelarCompromissoRequest request) {
        return ResponseEntity.ok(compromissoUsuariaService.cancelar(id, dataOriginal, request));
    }

    @PutMapping("/compromissos-usuaria/{id}/ocorrencias/{dataOriginal}")
    public ResponseEntity<CompromissoUsuariaDetalheResponse> editarCompromisso(
            @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataOriginal,
            @Valid @RequestBody EditarCompromissoRequest request) {
        return ResponseEntity.ok(compromissoUsuariaService.editar(id, dataOriginal, request));
    }
}
