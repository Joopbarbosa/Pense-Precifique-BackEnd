package com.penseprecifique.api.caixa;

import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.shared.dto.request.caixa.CancelarVendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/caixa")
@RequiredArgsConstructor
public class VendaCaixaController {

    private final VendaCaixaService vendaCaixaService;
    private final ItemCatalogoService itemCatalogoService;
    private final HistoricoVendasService historicoVendasService;

    /**
     * RN-NOVA-1 reaberta (V0.12.0, achado do teste manual) — busca de itens de Catálogo
     * disponíveis pra venda no Caixa, em paralelo a {@code GET /produtos?busca=&semCatalogo=true}
     * (Produto direto). Mesmo método de {@code ItemCatalogoService} já usado por
     * {@code GET /orcamentos/itens-catalogo} — sem {@code catalogoId}, busca em todos os catálogos
     * da usuária.
     */
    @GetMapping("/busca-itens-catalogo")
    public ResponseEntity<Page<ItemCatalogoBuscaResponse>> buscarItensCatalogo(
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean incluirInativos,
            @PageableDefault(size = 8) Pageable pageable) {
        return ResponseEntity.ok(itemCatalogoService.buscarParaOrcamento(null, busca, incluirInativos, pageable));
    }

    /** Retorna {@code VendaCaixaResponseDTO} no sucesso ou {@code ConfirmacaoEstoqueNegativoResponse}
     * quando há aviso de estoque negativo pendente de confirmação (RN-NOVA-2). */
    @PostMapping("/vendas")
    public ResponseEntity<Object> registrarVenda(@Valid @RequestBody VendaCaixaRequestDTO request) {
        return ResponseEntity.ok(vendaCaixaService.registrarVenda(request));
    }

    @GetMapping("/vendas/{id}")
    public ResponseEntity<VendaCaixaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(vendaCaixaService.buscarPorId(id));
    }

    @PostMapping("/vendas/{id}/cancelar")
    public ResponseEntity<VendaCaixaResponseDTO> cancelarVenda(
            @PathVariable UUID id, @Valid @RequestBody CancelarVendaCaixaRequestDTO request) {
        return ResponseEntity.ok(vendaCaixaService.cancelarVenda(id, request));
    }

    /**
     * V0.17.0 #973 (RN-NOVA-26) — histórico das vendas do Caixa de todos os turnos (concluídas e canceladas). Filtros: período,
     * status, forma de pagamento e busca; ordenação por coluna ({@code sort=campo,asc|desc}); 20 por página, de 1 a 100.
     */
    @GetMapping("/vendas/historico")
    public ResponseEntity<Page<HistoricoVendaResponse>> historico(
            @RequestParam(required = false) java.time.LocalDate de,
            @RequestParam(required = false) java.time.LocalDate ate,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String forma,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(historicoVendasService.listar(de, ate, status, forma, busca, sort, page, size));
    }

    @GetMapping("/turnos/{id}/vendas")
    public ResponseEntity<List<VendaCaixaResponseDTO>> listarPorTurno(@PathVariable UUID id) {
        return ResponseEntity.ok(vendaCaixaService.listarPorTurno(id));
    }
}
