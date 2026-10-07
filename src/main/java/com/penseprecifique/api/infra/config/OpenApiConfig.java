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
import java.util.Map;

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
}
