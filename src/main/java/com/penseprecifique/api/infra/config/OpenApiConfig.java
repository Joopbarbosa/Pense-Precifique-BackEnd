package com.penseprecifique.api.infra.config;

import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * #765 (V0.16.0, triagem do Schemathesis) — declara no OpenAPI as respostas de erro que a API sempre pode devolver,
 * no formato único do {@link ErrorResponseDTO}. Antes, só o 200 aparecia e o Schemathesis acusava 177 casos de
 * "status não documentado". Não muda comportamento: só descreve o que o {@code GlobalExceptionHandler} e o
 * Spring Security já fazem.
 */
@Configuration
public class OpenApiConfig {

    private static final Map<String, String> ERROS = new LinkedHashMap<>();

    static {
        ERROS.put("400", "Entrada inválida (BLOQUEIO) ou requisição malformada");
        ERROS.put("401", "Sem autenticação ou token inválido");
        ERROS.put("403", "Sem permissão para este recurso");
        ERROS.put("404", "Recurso não encontrado ou de outra conta");
        ERROS.put("405", "Método HTTP fora do contrato deste endereço");
        ERROS.put("415", "Tipo de conteúdo não aceito por este endereço");
        ERROS.put("500", "Erro interno não tratado");
    }

    @Bean
    public OpenApiCustomizer respostasDeErroPadrao() {
        return openApi -> {
            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(ErrorResponseDTO.class);
            if (openApi.getComponents() != null) {
                schemas.forEach((nome, schema) -> openApi.getComponents().addSchemas(nome, schema));
                ajustarErroPadrao(openApi.getComponents().getSchemas().get("ErrorResponseDTO"));
                ajustarMaiorAumento(openApi.getComponents().getSchemas().get("DashboardComprasResponse"));
            }
            if (openApi.getPaths() == null) {
                return;
            }
            Schema<?> referencia = new Schema<>().$ref("#/components/schemas/ErrorResponseDTO");
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(op -> adicionar(op, referencia)));
        };
    }

    private static void adicionar(Operation operacao, Schema<?> referencia) {
        ERROS.forEach((codigo, descricao) -> {
            if (operacao.getResponses().containsKey(codigo)) {
                return;
            }
            operacao.getResponses().addApiResponse(codigo, new ApiResponse().description(descricao)
                    .content(new Content().addMediaType("application/json", new MediaType().schema(referencia))));
        });
    }

    /**
     * #771 — o JSON real do erro tem {@code timestamp} sem fuso (LocalDateTime) e deixa {@code null} os campos da modal
     * que não se aplicam; o schema dizia {@code date-time} (exige fuso) e campos sempre preenchidos.
     */
    private static void ajustarErroPadrao(Schema<?> erro) {
        if (erro == null || erro.getProperties() == null) {
            return;
        }
        Schema<?> timestamp = (Schema<?>) erro.getProperties().get("timestamp");
        if (timestamp != null) {
            timestamp.setFormat("local-date-time");
        }
        for (String campo : List.of("fieldErrors", "titulo", "motivo", "comoResolver", "itens")) {
            Schema<?> propriedade = (Schema<?>) erro.getProperties().get(campo);
            if (propriedade != null) {
                aceitarNulo(propriedade);
            }
        }
    }

    /** #772 — {@code maiorAumento} é nulo sem variação no período; $ref + type null não vale em OpenAPI 3.1: usa anyOf. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void ajustarMaiorAumento(Schema<?> dashboard) {
        if (dashboard == null || dashboard.getProperties() == null) {
            return;
        }
        Map<String, Schema> propriedades = (Map<String, Schema>) (Map) dashboard.getProperties();
        propriedades.put("maiorAumento", new Schema<>().anyOf(List.of(
                new Schema<>().$ref("#/components/schemas/InsumoVariacao"),
                new Schema<>().types(Set.of("null")))));
    }

    private static void aceitarNulo(Schema<?> propriedade) {
        Set<String> tipos = new LinkedHashSet<>();
        if (propriedade.getTypes() != null) {
            tipos.addAll(propriedade.getTypes());
        } else if (propriedade.getType() != null) {
            tipos.add(propriedade.getType());
        }
        tipos.add("null");
        propriedade.setType(null);
        propriedade.setTypes(tipos);
    }
}
