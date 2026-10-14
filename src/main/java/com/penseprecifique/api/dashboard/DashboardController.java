package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.shared.domain.enums.TipoVendaCmv;
import com.penseprecifique.api.shared.dto.response.dashboard.DashboardResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardVendasService dashboardVendasService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    /** #719/RN-NOVA-17 (V0.17.0) — aba Gráficos do Dashboard de Vendas; sem datas, o mês atual. */
    @GetMapping("/vendas")
    public ResponseEntity<DashboardVendasResponse> vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(dashboardVendasService.dashboard(de, ate));
    }

    /** #719/RN-NOVA-17 — aba Vendas: todas as vendas e orçamentos, de qualquer status, com filtros e paginação. */
    @GetMapping("/vendas/lista")
    public ResponseEntity<ListaVendasResponse> listaVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) TipoVendaCmv origem,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean somenteQueContam,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(dashboardVendasService.lista(de, ate, origem, status, busca, somenteQueContam, pageable));
    }
}
