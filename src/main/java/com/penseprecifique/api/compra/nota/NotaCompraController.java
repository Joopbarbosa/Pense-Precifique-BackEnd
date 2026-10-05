package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaRascunhoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** V0.16.0 (#683, DT-NOVA-9) — registrar compra a partir de uma nota fiscal. */
@RestController
@RequestMapping("/compras/nota")
@RequiredArgsConstructor
public class NotaCompraController {

    private final NotaCompraService notaCompraService;

    /** Lê a nota (link do QR, chave ou arquivo). Não grava nada; devolve a proposta e a assinatura. */
    @PostMapping(value = "/leitura", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NotaLeituraResponse> ler(
            @RequestParam String modelo,
            @RequestParam(required = false) String qrUrl,
            @RequestParam(required = false) String chaveAcesso,
            @RequestParam(defaultValue = "false") boolean confirmouEnvioIa,
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        return ResponseEntity.ok(notaCompraService.ler(modelo, qrUrl, chaveAcesso, arquivo, confirmouEnvioIa));
    }

    /** Cria o rascunho (ou, com {@code simular=true}, só calcula). Com arquivo, usa multipart. */
    @PostMapping(value = "/rascunho", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NotaRascunhoResponse> rascunhoComArquivo(
            @RequestPart("dados") @Valid NotaRascunhoRequest dados,
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo,
            @RequestParam(defaultValue = "false") boolean simular) {
        return ResponseEntity.ok(notaCompraService.criarRascunho(dados, arquivo, simular));
    }

    /** Mesma rota quando o comprovante é só o link do QR (sem arquivo). */
    @PostMapping(value = "/rascunho", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotaRascunhoResponse> rascunhoComLink(
            @RequestBody @Valid NotaRascunhoRequest dados,
            @RequestParam(defaultValue = "false") boolean simular) {
        return ResponseEntity.ok(notaCompraService.criarRascunho(dados, null, simular));
    }
}
