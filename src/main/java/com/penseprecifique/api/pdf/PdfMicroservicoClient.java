package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Cliente HTTP pro microsserviço pense-precifique-pdf (contrato-pdf.md) — {@code POST}, não
 * {@code GET}, apesar do endpoint documentado como {@code GET /render/{tipo}/{id}?format=};
 * a seção 1 do contrato já rotula o corpo como "Body (POST, payload grande)" e a rota do
 * microsserviço aceita os dois verbos no mesmo handler — POST é o único dos dois que carrega
 * corpo de forma padrão em HTTP.
 */
@Component
@Slf4j
public class PdfMicroservicoClient {

    private static final String MENSAGEM_INDISPONIVEL =
            "Geração de documento temporariamente indisponível. Tente novamente em instantes.";
    private static final String MENSAGEM_LIMITE_PAGINAS =
            "Documento muito extenso. Entre em contato com o suporte.";
    private static final String MENSAGEM_ERRO_GENERICO = "Erro ao gerar PDF.";

    private final RestClient restClient;

    /**
     * {@code RestClient.builder()} (factory estático), não o bean {@code RestClient.Builder}
     * autoconfigurado por Spring — o request factory já é totalmente definido abaixo, então o
     * bean gerenciado não teria uso nenhum aqui.
     *
     * <p>Request factory é {@code JdkClientHttpRequestFactory} (JDK {@code java.net.http.HttpClient},
     * disponível desde o Java 11, sem dependência externa), construído manualmente — não via
     * {@code ClientHttpRequestFactories.get(...)} — para forçar {@code HTTP_1_1} explicitamente.
     * O default do JDK ({@code HttpClient.Version.HTTP_2}, com fallback automático) gera
     * negociação HTTP/2 que o pense-precifique-pdf (Express puro, HTTP/1.1 apenas — igual
     * qualquer servidor Jetty/Node comum sem suporte a h2c explícito) não sustenta: confirmado em
     * teste com WireMock, onde a negociação HTTP/2 terminava em "RST_STREAM: Stream cancelled" a
     * cada requisição — mesmo risco existiria contra o microsserviço real.
     */
    public PdfMicroservicoClient(
            @Value("${pdf.microservice.base-url}") String baseUrl,
            @Value("${pdf.microservice.timeout-seconds:30}") long timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public byte[] gerarPdf(String tipo, UUID id, Object payload) {
        return renderizar(tipo, id, payload, "pdf");
    }

    /**
     * Mesmo endpoint de {@link #gerarPdf}, só muda o {@code format} — usado pelo preview
     * (Fluxo E do PRD), que exibe o HTML retornado em vez de gerar o binário via Puppeteer.
     *
     * <p>Lê sempre como {@code byte[]} e decodifica explicitamente em UTF-8, em vez de pedir
     * {@code .body(String.class)} — o {@code StringHttpMessageConverter} do {@code RestClient}
     * cai no charset default (ISO-8859-1) quando o microsserviço não declara {@code charset} no
     * header {@code Content-Type: text/html}, corrompendo qualquer acentuação (nome de cliente,
     * empresa, produto — comum em dado PT-BR). Confirmado via teste de integração antes desta
     * correção.
     */
    public String gerarHtml(String tipo, UUID id, Object payload) {
        byte[] bytes = renderizar(tipo, id, payload, "html");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] renderizar(String tipo, UUID id, Object payload, String format) {
        try {
            return restClient.post()
                    .uri("/render/{tipo}/{id}?format={format}", tipo, id, format)
                    .header("X-User-Token", tokenAtual())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException ex) {
            throw traduzirErroHttp(ex);
        } catch (ResourceAccessException ex) {
            // Conexão recusada ou timeout do próprio cliente (pdf.microservice.timeout-seconds
            // estourado) — do ponto de vista do usuário, mesma mensagem do 503/408 do microsserviço.
            log.warn("Falha de conexão com o microsserviço de PDF: {}", ex.getMessage());
            throw new BusinessException(MENSAGEM_INDISPONIVEL);
        }
    }

    private RuntimeException traduzirErroHttp(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();

        if (status.value() == 413) {
            return new BusinessException(MENSAGEM_LIMITE_PAGINAS);
        }
        if (status.value() == 408 || status.is5xxServerError()) {
            log.warn("Microsserviço de PDF indisponível ou em timeout (status={})", status.value());
            return new BusinessException(MENSAGEM_INDISPONIVEL);
        }

        // 400 e demais 4xx inesperados indicam bug de integração (payload mal montado do nosso
        // lado) — não é erro esperado do usuário, então loga detalhado e responde genérico.
        log.error("Microsserviço de PDF rejeitou o payload (status={}): {}",
                status.value(), ex.getResponseBodyAsString());
        return new BusinessException(MENSAGEM_ERRO_GENERICO);
    }

    private String tokenAtual() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        String header = attrs.getRequest().getHeader("Authorization");
        if (!StringUtils.hasText(header)) {
            return "";
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
