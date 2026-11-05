package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Base64;
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

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
    @Autowired
    public PdfMicroservicoClient(
            @Value("${pdf.microservice.base-url}") String baseUrl,
            @Value("${pdf.microservice.timeout-seconds:30}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    public PdfMicroservicoClient(String baseUrl, long timeoutSeconds) {
        this(baseUrl, timeoutSeconds, new ObjectMapper());
    }

    public byte[] gerarPdf(String tipo, UUID id, Object payload) {
        return renderizar(tipo, id, payload, "pdf");
    }

    public PdfGeracaoResultado gerarPdfControlado(String tipo, UUID id, Object payload, boolean dividir) {
        try {
            ResponseEntity<byte[]> resposta = restClient.post()
                    .uri("/render/{tipo}/{id}?format=pdf&dividir={dividir}", tipo, id, dividir)
                    .header("X-User-Token", tokenAtual())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> true, (request, response) -> { })
                    .toEntity(byte[].class);
            byte[] corpo = resposta.getBody();
            int paginas = Integer.parseInt(resposta.getHeaders().getFirst("X-Pdf-Page-Count") == null
                    ? "0" : resposta.getHeaders().getFirst("X-Pdf-Page-Count"));
            if (resposta.getStatusCode().value() == 409 || resposta.getStatusCode().value() == 413) {
                var json = objectMapper.readTree(corpo);
                throw new PdfControleException(json.path("tipo").asText(), json.path("message").asText(),
                        json.path("totalPaginas").asInt(paginas), json.path("dividirDisponivel").asBoolean(false));
            }
            if (resposta.getStatusCode().is2xxSuccessful() && resposta.getHeaders().getContentType() != null
                    && MediaType.APPLICATION_JSON.isCompatibleWith(resposta.getHeaders().getContentType())) {
                var json = objectMapper.readTree(corpo);
                java.util.List<PdfParte> partes = objectMapper.convertValue(json.get("partes"),
                        new tools.jackson.core.type.TypeReference<java.util.List<PdfParte>>() { });
                return new PdfGeracaoResultado(paginas, null, partes);
            }
            if (!resposta.getStatusCode().is2xxSuccessful()) throw renderizarErro(resposta.getStatusCode());
            return new PdfGeracaoResultado(paginas, corpo, List.of());
        } catch (PdfControleException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw traduzirErroHttp(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Falha de conexão com o microsserviço de PDF: {}", ex.getMessage());
            throw new BusinessException(MENSAGEM_INDISPONIVEL);
        } catch (Exception ex) {
            log.error("Resposta inválida do microsserviço de PDF", ex);
            throw new BusinessException(MENSAGEM_ERRO_GENERICO);
        }
    }

    private RuntimeException renderizarErro(HttpStatusCode status) {
        if (status.value() == 408 || status.is5xxServerError()) return new BusinessException(MENSAGEM_INDISPONIVEL);
        return new BusinessException(MENSAGEM_ERRO_GENERICO);
    }

    public record PdfGeracaoResultado(int totalPaginas, byte[] pdf, List<PdfParte> partes) { }
    public record PdfParte(int numeroParte, int totalPaginas, String pdfBase64) {
        public byte[] bytes() { return Base64.getDecoder().decode(pdfBase64); }
    }

    public static class PdfControleException extends RuntimeException {
        private final String tipo;
        private final int totalPaginas;
        private final boolean dividirDisponivel;
        public PdfControleException(String tipo, String message, int totalPaginas, boolean dividirDisponivel) {
            super(message); this.tipo = tipo; this.totalPaginas = totalPaginas; this.dividirDisponivel = dividirDisponivel;
        }
        public String getTipo() { return tipo; }
        public int getTotalPaginas() { return totalPaginas; }
        public boolean isDividirDisponivel() { return dividirDisponivel; }
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
