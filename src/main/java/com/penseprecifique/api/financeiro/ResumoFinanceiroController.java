package com.penseprecifique.api.financeiro;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V0.19.0 (#1176, UC-NOVO-3) — resumo do mês, gráficos, projeção e próximos vencimentos. Somente leitura. */
@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
public class ResumoFinanceiroController {

    private final ResumoFinanceiroService service;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoFinanceiroResponse> resumo(@RequestParam(required = false) String mes) {
        return ResponseEntity.ok(service.resumo(mes));
    }

    @GetMapping("/proximos-vencimentos")
    public ResponseEntity<ListaVencimentosResponse> proximosVencimentos(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(service.listarVencimentos(pageable));
    }
}
