package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.dto.request.caixa.CancelarVendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/caixa")
@RequiredArgsConstructor
public class VendaCaixaController {

    private final VendaCaixaService vendaCaixaService;

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

    @GetMapping("/turnos/{id}/vendas")
    public ResponseEntity<List<VendaCaixaResponseDTO>> listarPorTurno(@PathVariable UUID id) {
        return ResponseEntity.ok(vendaCaixaService.listarPorTurno(id));
    }
}
