package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.shared.domain.enums.TipoVendaCmv;
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

    private final DashboardVendasService dashboardVendasService;
    private final DashboardCadastrosService dashboardCadastrosService;

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
            @RequestParam(required = false) String produtoId,
            @RequestParam(required = false) String forma,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(dashboardVendasService.lista(de, ate, origem, status, busca, somenteQueContam, produtoId, forma, pageable));
    }

    /** #607/RN-NOVA-16 (V0.17.0) — Dashboard de Cadastros; sem datas, o mês atual; {@code insumoId} escolhe o insumo da evolução de preço. */
    @GetMapping("/cadastros")
    public ResponseEntity<DashboardCadastrosResponse> cadastros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) java.util.UUID insumoId) {
        return ResponseEntity.ok(dashboardCadastrosService.dashboard(de, ate, insumoId));
    }

    /** #607 — evolução do preço médio mensal de um insumo escolhido na tela. */
    @GetMapping("/cadastros/evolucao-preco")
    public ResponseEntity<DashboardCadastrosResponse.EvolucaoPreco> evolucaoPreco(
            @RequestParam java.util.UUID insumoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(dashboardCadastrosService.evolucaoDoInsumo(insumoId, de, ate));
    }

    /** #989 (RN-NOVA-29) — evolução do preço médio mensal de 1 a 5 insumos escolhidos, uma série por insumo. */
    @GetMapping("/cadastros/evolucao-preco-insumos")
    public ResponseEntity<DashboardCadastrosResponse.EvolucaoPrecoInsumos> evolucaoPrecoInsumos(
            @RequestParam(required = false) java.util.List<java.util.UUID> insumoIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(dashboardCadastrosService.evolucaoDosInsumos(insumoIds, de, ate));
    }
}
