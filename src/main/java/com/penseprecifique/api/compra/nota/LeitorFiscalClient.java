package com.penseprecifique.api.compra.nota;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V0.16.0 (#683, DT-NOVA-9) — cliente HTTP síncrono do leitor-fiscal, no mesmo desenho de
 * {@code PdfMicroservicoClient}: HTTP/1.1, endereço e chave do produto por configuração. Só o backend
 * fala com o serviço (nunca o navegador). Erro funcional do serviço ({@code tipo} BLOQUEIO) vira
 * {@link BusinessException} com os campos da modal de erro; falha de rede, 5xx ou tempo esgotado vira
 * BLOQUEIO genérico, sem detalhe interno (RN-NOVA-4, RN-085).
 */
@Component
@Slf4j
public class LeitorFiscalClient {

    static final String MSG_INDISPONIVEL = "Não foi possível ler a nota agora.";
    static final String TITULO_FALHA = "Leitura da nota indisponível";
    static final String MOTIVO_FALHA = "O leitor de notas não respondeu ou não conseguiu ler este documento.";
    static final String COMO_RESOLVER_FALHA = "Tente novamente com um novo QR code, uma nova foto ou um novo arquivo, ou registre a compra à mão.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chaveProduto;

    @Autowired
    public LeitorFiscalClient(
            @Value("${leitor-fiscal.base-url}") String baseUrl,
            @Value("${leitor-fiscal.chave-produto:}") String chaveProduto,
            @Value("${leitor-fiscal.timeout-seconds:70}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.chaveProduto = chaveProduto;
    }

    /**
     * Pede a leitura de uma nota. Exatamente uma entrada: {@code qrUrl}, {@code chaveAcesso} ou {@code arquivo}.
     *
     * @param contaId identificador opaco da conta (UUID do usuário), só para o serviço contar o uso de IA
     */
    public NotaLida ler(String modelo, String qrUrl, String chaveAcesso, ArquivoLeitura arquivo,
                        boolean confirmouEnvioIa, UUID contaId) {
        if (!StringUtils.hasText(chaveProduto)) {
            log.error("leitor-fiscal.chave-produto não configurada; leitura de nota indisponível.");
            throw falhaGenerica();
        }
        try {
            RestClient.RequestBodySpec pedido = restClient.post().uri("/v1/leituras")
                    .header("Authorization", "Bearer " + chaveProduto)
                    .header("X-Conta-Id", contaId.toString());
            String json;
            if (arquivo != null) {
                // contrato (openapi.yaml da #677): arquivo vai em multipart só com modelo, arquivo e confirmouEnvioIa
                MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
                corpo.add("modelo", modelo);
                corpo.add("confirmouEnvioIa", String.valueOf(confirmouEnvioIa));
                corpo.add("arquivo", new ByteArrayResource(arquivo.conteudo()) {
                    @Override
                    public String getFilename() {
                        return arquivo.nome();
                    }
                });
                json = pedido.contentType(MediaType.MULTIPART_FORM_DATA).body(corpo).retrieve().body(String.class);
            } else {
                // qrUrl ou chaveAcesso vão em JSON, exatamente um dos dois
                Map<String, String> corpo = new LinkedHashMap<>();
                corpo.put("modelo", modelo);
                if (StringUtils.hasText(qrUrl)) {
                    corpo.put("qrUrl", qrUrl);
                } else {
                    corpo.put("chaveAcesso", chaveAcesso);
                }
                json = pedido.contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(String.class);
            }
            return objectMapper.readValue(json, NotaLida.class);
        } catch (RestClientResponseException e) {
            throw traduzir(e);
        } catch (ResourceAccessException e) {
            log.warn("leitor-fiscal inacessível ou lento: {}", e.getMessage());
            throw falhaGenerica();
        } catch (Exception e) {
            log.warn("resposta ilegível do leitor-fiscal: {}", e.getMessage());
            throw falhaGenerica();
        }
    }

    /** 400 com {@code tipo} BLOQUEIO repassa os campos do modal; 401/5xx e demais viram falha genérica. */
    private BusinessException traduzir(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 400) {
            try {
                JsonNode erro = objectMapper.readTree(e.getResponseBodyAsString());
                String titulo = texto(erro, "titulo");
                String motivo = texto(erro, "motivo");
                String comoResolver = texto(erro, "comoResolver");
                if (titulo != null && motivo != null && comoResolver != null) {
                    BusinessException negocio = BusinessException.explicado(titulo, motivo, motivo, comoResolver);
                    List<String> itens = new ArrayList<>();
                    JsonNode listaItens = erro.get("itens");
                    if (listaItens != null && listaItens.isArray()) {
                        listaItens.forEach(i -> itens.add(i.asText()));
                    }
                    return itens.isEmpty() ? negocio : negocio.comItens(itens);
                }
            } catch (Exception ignorada) {
                log.debug("corpo de erro do leitor-fiscal fora do formato esperado", ignorada);
            }
        }
        log.warn("leitor-fiscal respondeu {}", status);
        return falhaGenerica();
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null;
    }

    private static BusinessException falhaGenerica() {
        return BusinessException.explicado(TITULO_FALHA, MSG_INDISPONIVEL, MOTIVO_FALHA, COMO_RESOLVER_FALHA);
    }

    /** Arquivo a ler: nome para o serviço e conteúdo em bytes. */
    public record ArquivoLeitura(String nome, byte[] conteudo) {}
}
