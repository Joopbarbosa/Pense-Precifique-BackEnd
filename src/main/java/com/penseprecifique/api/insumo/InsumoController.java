package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRascunhoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.ResolverVinculosInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoContagensResponse;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.ProdutoRelacionadoResponse;
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
@RequestMapping("/insumos")
@RequiredArgsConstructor
public class InsumoController {

    private final InsumoService insumoService;

    @GetMapping
    public ResponseEntity<Page<InsumoResponseDTO>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            // #616 (adendo 2) — seletores: ativos e inativos, ativos primeiro
            @RequestParam(defaultValue = "false") boolean incluirInativos,
            // V0.16.0 (#687, DT-NOVA-12) — listagem de Insumos e seletores da compra incluem rascunhos
            @RequestParam(defaultValue = "false") boolean incluirRascunhos,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(insumoService.listar(busca, ativo, incluirInativos, incluirRascunhos, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InsumoResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(insumoService.buscarPorId(id));
    }

    // RN-NOVA-4 (V0.10.0, #336) — contadores por filtro da Lista de Insumos, agregados no backend.
    @GetMapping("/contagens")
    public ResponseEntity<InsumoContagensResponse> contagens() {
        return ResponseEntity.ok(insumoService.contagens());
    }

    @PostMapping
    public ResponseEntity<InsumoResponseDTO> cadastrar(@Valid @RequestBody InsumoCreateRequestDTO request) {
        return ResponseEntity.status(201).body(insumoService.cadastrar(request));
    }

    /** V0.16.0 (#687, RN-NOVA-18) — insumo em rascunho a partir do item da nota; {@code simular} só propõe. */
    @PostMapping("/rascunho")
    public ResponseEntity<InsumoResponseDTO> criarRascunho(
            @Valid @RequestBody InsumoRascunhoRequestDTO request,
            @RequestParam(defaultValue = "false") boolean simular) {
        InsumoResponseDTO resposta = insumoService.criarRascunho(request, simular);
        return simular ? ResponseEntity.ok(resposta) : ResponseEntity.status(201).body(resposta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InsumoResponseDTO> editar(
            @PathVariable UUID id,
            @Valid @RequestBody InsumoRequestDTO request) {
        return ResponseEntity.ok(insumoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        insumoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        insumoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable UUID id) {
        insumoService.reativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/movimentacoes")
    public ResponseEntity<Page<MovimentacaoInsumoResponseDTO>> listarMovimentacoes(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(insumoService.listarMovimentacoes(id, pageable));
    }

    @PostMapping("/{id}/baixa-manual")
    public ResponseEntity<MovimentacaoInsumoResponseDTO> baixaManual(
            @PathVariable UUID id,
            @Valid @RequestBody BaixaManualInsumoRequestDTO request) {
        return ResponseEntity.status(201).body(insumoService.baixaManual(id, request));
    }

    @GetMapping("/{id}/produtos-relacionados")
    public ResponseEntity<List<ProdutoRelacionadoResponse>> listarProdutosRelacionados(@PathVariable UUID id) {
        return ResponseEntity.ok(insumoService.listarProdutosRelacionados(id));
    }

    @PostMapping("/{id}/resolver-vinculos")
    public ResponseEntity<Void> resolverVinculos(
            @PathVariable UUID id,
            @Valid @RequestBody ResolverVinculosInsumoRequestDTO request) {
        insumoService.resolverVinculos(id, request);
        return ResponseEntity.noContent().build();
    }
}
