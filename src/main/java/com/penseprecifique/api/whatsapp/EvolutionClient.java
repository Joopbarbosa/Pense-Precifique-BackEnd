package com.penseprecifique.api.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Cliente HTTP da Evolution API (v2), mesmo molde do {@code PdfMicroservicoClient}. A chave
 * ({@code EVOLUTION_API_KEY}) só vai no cabeçalho {@code apikey}; nunca em log, resposta ou mensagem de erro.
 */
@Slf4j
@Component
public class EvolutionClient implements WhatsappCanal {

    private final RestClient restClient;

    public EvolutionClient(
            @Value("${evolution.base-url}") String baseUrl,
            @Value("${evolution.api-key:}") String apiKey,
            @Value("${evolution.timeout-seconds:30}") long timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("apikey", apiKey == null ? "" : apiKey)
                .build();
    }

    @Override
    public void criarInstancia(String instancia) {
        executar(() -> restClient.post().uri("/instance/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("instanceName", instancia, "qrcode", true, "integration", "WHATSAPP-BAILEYS"))
                .retrieve().toBodilessEntity());
    }

    @Override
    @SuppressWarnings("unchecked")
    public EstadoCanal consultarEstado(String instancia) {
        Map<String, Object> corpo = executar(() -> restClient.get()
                .uri("/instance/connectionState/{i}", instancia).retrieve().body(Map.class));
        Object inst = corpo == null ? null : corpo.get("instance");
        Object estado = inst instanceof Map<?, ?> m ? m.get("state") : null;
        if ("open".equals(estado)) return EstadoCanal.ABERTO;
        if ("connecting".equals(estado)) return EstadoCanal.CONECTANDO;
        return EstadoCanal.FECHADO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String obterQr(String instancia) {
        Map<String, Object> corpo = executar(() -> restClient.get()
                .uri("/instance/connect/{i}", instancia).retrieve().body(Map.class));
        Object base64 = corpo == null ? null : corpo.get("base64");
        return base64 instanceof String s && !s.isBlank() ? s : null;
    }

    @Override
    public void desconectar(String instancia) {
        executar(() -> restClient.delete().uri("/instance/logout/{i}", instancia).retrieve().toBodilessEntity());
    }

    @Override
    public void enviarDocumento(String instancia, String numeroCanonico, byte[] pdf, String nomeArquivo, String legenda) {
        Map<String, Object> corpo = Map.of(
                "number", numeroCanonico,
                "mediatype", "document",
                "mimetype", "application/pdf",
                "caption", legenda == null ? "" : legenda,
                "fileName", nomeArquivo,
                "media", Base64.getEncoder().encodeToString(pdf));
        executar(() -> restClient.post().uri("/message/sendMedia/{i}", instancia)
                .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().toBodilessEntity());
    }

    private <T> T executar(java.util.function.Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String corpo = ex.getResponseBodyAsString();
            log.warn("Evolution API respondeu status={}", status);
            if (status == 400 && corpo != null && corpo.contains("\"exists\":false")) {
                throw new WhatsappCanalException(WhatsappCanalException.Tipo.NUMERO_SEM_WHATSAPP);
            }
            if (status == 404) {
                throw new WhatsappCanalException(WhatsappCanalException.Tipo.DESCONECTADO);
            }
            throw new WhatsappCanalException(WhatsappCanalException.Tipo.INDISPONIVEL);
        } catch (RestClientException ex) {
            log.warn("Evolution API indisponível ({})", ex.getClass().getSimpleName());
            throw new WhatsappCanalException(WhatsappCanalException.Tipo.INDISPONIVEL);
        }
    }
}
