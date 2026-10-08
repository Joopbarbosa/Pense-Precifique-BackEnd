package com.penseprecifique.api.compra.nota;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.16.0 (#681, RN-NOVA-12 passo 3, DT-NOVA-11) — sugestão de insumo por IA, no produto, num provedor
 * compatível com OpenAI (chat completions). Uma chamada por nota, só com os itens sem ligação; envia só
 * nomes de itens e de insumos, nunca dados pessoais. A resposta é validada: só vale id que estava na lista
 * enviada e fator maior que zero. Qualquer falha devolve lista vazia (a leitura segue sem sugestão).
 * Desligada por padrão ({@code sugestao-ia.habilitada}) até a política de privacidade (#675).
 */
@Component
@Slf4j
public class SugestaoInsumoIaClient {

    static final int MAX_CANDIDATOS = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean habilitada;
    private final String chave;
    private final String modelo;

    @Autowired
    public SugestaoInsumoIaClient(
            @Value("${sugestao-ia.habilitada:false}") boolean habilitada,
            @Value("${sugestao-ia.base-url:}") String baseUrl,
            @Value("${sugestao-ia.chave:}") String chave,
            @Value("${sugestao-ia.modelo:}") String modelo,
            @Value("${sugestao-ia.timeout-seconds:20}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost")
                .requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.habilitada = habilitada && StringUtils.hasText(baseUrl) && StringUtils.hasText(chave) && StringUtils.hasText(modelo);
        this.chave = chave;
        this.modelo = modelo;
    }

    public boolean habilitada() {
        return habilitada;
    }

    /** DT-NOVA-11 — só o nome do item vai ao provedor; a posição é a chave técnica para casar a resposta. */
    public record ItemPedido(int posicao, String nome) {}

    /** Só o nome do insumo (com a marca, que faz parte da identidade dele) vai ao provedor; o id é a chave técnica da resposta. */
    public record Candidato(UUID id, String nome) {}

    public record Sugestao(int posicao, UUID insumoId, BigDecimal fator) {}

    public List<Sugestao> sugerir(List<ItemPedido> itens, List<Candidato> candidatos) {
        if (!habilitada || itens.isEmpty() || candidatos.isEmpty()) {
            return List.of();
        }
        try {
            String conteudo = restClient.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + chave)
                    .body(corpo(itens, candidatos))
                    .retrieve()
                    .body(String.class);
            return validar(conteudo, itens, candidatos);
        } catch (Exception e) {
            log.warn("sugestão de insumo por IA indisponível: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> corpo(List<ItemPedido> itens, List<Candidato> candidatos) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("itens", itens);
        dados.put("insumos", candidatos);
        String instrucao = "Você liga itens de nota fiscal de compra a insumos cadastrados por uma artesã. "
                + "Para cada item, escolha no máximo um insumo da lista (pelo id) que seja o mesmo produto, e o fator: "
                + "quantas unidades do insumo vêm em uma unidade do item (ex.: pacote com 100 folhas = 100). "
                + "Se não houver insumo claramente igual, não sugira. Responda só JSON no formato "
                + "{\"sugestoes\":[{\"posicao\":0,\"insumoId\":\"...\",\"fator\":1}]}.";
        return Map.of(
                "model", modelo,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", instrucao),
                        Map.of("role", "user", "content", objectMapper.writeValueAsString(dados))));
    }

    /** Só aceita posição pedida, id da lista enviada e fator maior que zero; uma sugestão por posição. */
    List<Sugestao> validar(String respostaBruta, List<ItemPedido> itens, List<Candidato> candidatos) throws Exception {
        JsonNode raiz = objectMapper.readTree(respostaBruta);
        String texto = raiz.path("choices").path(0).path("message").path("content").asText("");
        JsonNode lista = objectMapper.readTree(texto).path("sugestoes");
        if (!lista.isArray()) {
            return List.of();
        }
        Set<Integer> posicoes = itens.stream().map(ItemPedido::posicao).collect(Collectors.toSet());
        Set<UUID> ids = candidatos.stream().map(Candidato::id).collect(Collectors.toSet());
        Map<Integer, Sugestao> porPosicao = new LinkedHashMap<>();
        for (JsonNode s : lista) {
            try {
                int posicao = s.path("posicao").asInt(-1);
                UUID id = UUID.fromString(s.path("insumoId").asText(""));
                BigDecimal fator = new BigDecimal(s.path("fator").asText(""));
                if (posicoes.contains(posicao) && ids.contains(id) && fator.signum() > 0
                        && fator.compareTo(new BigDecimal("100000")) <= 0) {
                    porPosicao.putIfAbsent(posicao, new Sugestao(posicao, id, fator.setScale(4, java.math.RoundingMode.HALF_UP)));
                }
            } catch (RuntimeException ignorada) {
                log.debug("sugestão da IA fora do formato, descartada");
            }
        }
        return new ArrayList<>(porPosicao.values());
    }
}
