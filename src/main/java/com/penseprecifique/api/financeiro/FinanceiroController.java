package com.penseprecifique.api.financeiro;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

/** V0.19.0 (#1172, RN-NOVA-3 e 4, UC-NOVO-1) — listas A pagar / A receber e baixa de parcela. Não há criação por HTTP nesta tarefa (origens vêm em F2 a F4). */
@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final ParcelaFinanceiraService parcelaService;
    private final LancamentoFinanceiroService lancamentoService;
    private final com.penseprecifique.api.orcamento.OrcamentoService orcamentoService;

    @PostMapping("/lancamentos-avulsos")
    public ResponseEntity<Void> criarAvulso(@Valid @RequestBody ContaAvulsaRequest request) {
        lancamentoService.criarContaAvulsa(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/parcelas/{id}/avulso")
    public ResponseEntity<ParcelaFinanceiraResponse> editarAvulso(@PathVariable UUID id,
            @Valid @RequestBody EditarOcorrenciaRequest request,
            @RequestParam(defaultValue = "false") boolean seguintes) {
        lancamentoService.editarOcorrencia(id, request, seguintes);
        return ResponseEntity.ok(parcelaService.buscar(id));
    }

    @DeleteMapping("/parcelas/{id}/avulso")
    public ResponseEntity<Void> excluirAvulso(@PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean seguintes) {
        lancamentoService.excluirOcorrencia(id, seguintes);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parcelas/{id}/avulso/impacto-exclusao")
    public ResponseEntity<ImpactoExclusaoAvulsaResponse> preverExclusaoAvulso(@PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean seguintes) {
        return ResponseEntity.ok(lancamentoService.preverExclusaoOcorrencia(id, seguintes));
    }

    @PutMapping("/lancamentos/{id}/fora-projecao")
    public ResponseEntity<Void> marcarForaProjecao(@PathVariable UUID id, @Valid @RequestBody ForaProjecaoRequest request) {
        lancamentoService.marcarForaProjecao(id, request.foraProjecao());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parcelas")
    public ResponseEntity<ListaParcelasResponse> listar(
            @RequestParam TipoLancamento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) FiltroSituacaoParcela situacao,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(parcelaService.listar(tipo, de, ate, situacao, pageable));
    }

    @GetMapping("/parcelas/{id}")
    public ResponseEntity<ParcelaFinanceiraResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(parcelaService.buscar(id));
    }

    @PostMapping("/parcelas/{id}/baixa")
    public ResponseEntity<ParcelaFinanceiraResponse> baixar(@PathVariable UUID id, @Valid @RequestBody BaixaParcelaRequest request) {
        // V0.19.0 (#1174, RN-NOVA-11) — Sinal/Saldo/Total de orçamento são recebidos pela transição do orçamento
        var doOrcamento = lancamentoService.orcamentoDaParcela(id);
        if (doOrcamento.isPresent()) {
            orcamentoService.receberParcela(doOrcamento.get(), request.dataBaixa(), request.metodoPagamentoId());
            return ResponseEntity.ok(parcelaService.buscar(id));
        }
        Parcela p = lancamentoService.baixar(id, request.dataBaixa(), request.metodoPagamentoId());
        return ResponseEntity.ok(parcelaService.paraResposta(p));
    }

    @PostMapping("/parcelas/{id}/desfazer-baixa")
    public ResponseEntity<ParcelaFinanceiraResponse> desfazerBaixa(@PathVariable UUID id) {
        Parcela p = lancamentoService.desfazerBaixa(id);
        return ResponseEntity.ok(parcelaService.paraResposta(p));
    }
}
