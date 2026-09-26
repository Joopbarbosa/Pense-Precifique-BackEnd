package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteContagensResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteGraficosResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteIndicadoresResponse;
import com.penseprecifique.api.shared.dto.response.cliente.CompraFornecedorResponse;
import com.penseprecifique.api.shared.dto.response.cliente.PedidoClienteResponse;
import org.springframework.format.annotation.DateTimeFormat;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteHistoricoService clienteHistoricoService;

    // #356 — padronizado para 'busca' (mesmo nome usado por Produto/Insumo/Orçamento/Catálogo/Produção);
    // era 'nome' até V0.8.2, único endpoint divergente do padrão do resto do sistema.
    @GetMapping
    // #536/#538 (V0.15.0) — ativo omitido = só ativos (padrão dos seletores); ativo=false = só
    // inativos. papel omitido = os dois papéis.
    public ResponseEntity<Page<ClienteResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) PapelCadastro papel,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(busca, ativo, papel, pageable));
    }

    // #537 (V0.15.0) — contadores dos filtros da tela, mesmo padrão de GET /insumos/contagens.
    @GetMapping("/contagens")
    public ResponseEntity<ClienteContagensResponse> contagens() {
        return ResponseEntity.ok(clienteService.contagens());
    }

    // #560 (V0.15.0) — página de detalhe: indicadores por papel e histórico (DT-NOVA-9).
    @GetMapping("/{id}/indicadores")
    public ResponseEntity<ClienteIndicadoresResponse> indicadores(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteHistoricoService.indicadores(id));
    }

    @GetMapping("/{id}/historico/pedidos")
    public ResponseEntity<Page<PedidoClienteResponse>> historicoPedidos(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(clienteHistoricoService.historicoPedidos(id, pageable));
    }

    @GetMapping("/{id}/historico/compras")
    public ResponseEntity<Page<CompraFornecedorResponse>> historicoCompras(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(clienteHistoricoService.historicoCompras(id, pageable));
    }

    // #451 (V0.15.0) — gráficos do cliente; sem de/ate = últimos 12 meses.
    @GetMapping("/{id}/graficos")
    public ResponseEntity<ClienteGraficosResponse> graficos(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(clienteHistoricoService.graficos(id, de, ate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> cadastrar(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(201).body(clienteService.cadastrar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.editar(id, request));
    }

    // #538/RN-NOVA-2 (V0.15.0) — inativar é reversível, padrão do Insumo. O DELETE /clientes/{id}
    // saiu: não existe excluir cadastro.
    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        clienteService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable UUID id) {
        clienteService.reativar(id);
        return ResponseEntity.noContent().build();
    }
}
