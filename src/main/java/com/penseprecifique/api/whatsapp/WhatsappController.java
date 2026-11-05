package com.penseprecifique.api.whatsapp;

import com.penseprecifique.api.whatsapp.WhatsappDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** #1170 — conexão (Configurações > WhatsApp) e envio de PDF. A chave da Evolution nunca aparece aqui. */
@RestController
@RequestMapping("/whatsapp")
@RequiredArgsConstructor
public class WhatsappController {

    private final WhatsappConexaoService conexaoService;
    private final WhatsappEnvioService envioService;

    @GetMapping("/status")
    public StatusResponse status() { return conexaoService.status(); }

    @PostMapping("/conexao")
    public QrResponse conectar(@RequestBody(required = false) ConectarRequest request) {
        return conexaoService.conectar(request == null ? null : request.entendiRiscos());
    }

    @GetMapping("/conexao/qr")
    public QrResponse qr() { return conexaoService.qr(); }

    @DeleteMapping("/conexao")
    public ResponseEntity<Void> desconectar() {
        conexaoService.desconectar();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/envios")
    public EnvioResponse enviar(@RequestBody(required = false) EnvioRequest request) {
        return envioService.enviar(request);
    }

    @GetMapping("/envios")
    public List<HistoricoItem> historico() { return envioService.historico(); }
}
