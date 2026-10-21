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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** V0.18.0 (DT-NOVA-2) — Agenda: compromissos do período, faixa de atrasados e de hoje, lista de atrasados. */
@RestController
@RequestMapping("/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

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
}
