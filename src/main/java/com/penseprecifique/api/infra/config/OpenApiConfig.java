package com.penseprecifique.api.infra.config;

import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.ParameterizedType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
        // #775 — a API serializa LocalDateTime sem fuso (ex.: 2026-10-07T16:05:59); "date-time" do OpenAPI exige fuso.
        SpringDocUtils.getConfig().replaceWithSchema(LocalDateTime.class,
                new Schema<String>().type("string").format("local-date-time").example("2026-10-07T16:05:59"));
        ERROS.put("400", "Entrada inválida (BLOQUEIO) ou requisição malformada");
        ERROS.put("401", "Sem autenticação ou token inválido");
        ERROS.put("403", "Sem permissão para este recurso");
        ERROS.put("404", "Recurso não encontrado ou de outra conta");
        ERROS.put("405", "Método HTTP fora do contrato deste endereço");
        ERROS.put("415", "Tipo de conteúdo não aceito por este endereço");
        ERROS.put("500", "Erro interno não tratado");
    }

    /**
     * #775 — {@code ResponseEntity<Void>} é "204 sem conteúdo" nesta API ({@code noContent()}); sem isto o contrato dizia 200.
     * Quem responde 200 com corpo vazio declara o 200 com {@code @ApiResponse} no método.
     */
    @Bean
    public OperationCustomizer semConteudoComoRespostaPadrao() {
        return (operacao, metodo) -> {
            if (metodo.getMethod().getGenericReturnType() instanceof ParameterizedType tipo
                    && tipo.getRawType() == ResponseEntity.class
                    && tipo.getActualTypeArguments()[0] == Void.class
                    && !metodo.hasMethodAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse.class)) {
                ApiResponses respostas = operacao.getResponses() != null ? operacao.getResponses() : new ApiResponses();
                respostas.remove("200");
                respostas.addApiResponse("204", new ApiResponse().description("Sem conteúdo"));
                operacao.setResponses(respostas);
            }
            return operacao;
        };
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
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(op -> {
                adicionar(op, referencia);
                ajustarParametros(op);
            }));
        };
    }

    /**
     * #780 e #782 — o contrato descreve o que a API faz de fato com a consulta:
     * (a) {@code Pageable} saía como um parâmetro-objeto obrigatório chamado "pageable" (que não existe na URL) com
     * mínimos que a API não aplica; os parâmetros reais são {@code page}, {@code size} e {@code sort}, e valores
     * inválidos (page negativo, size menor que 1) usam o padrão em vez de dar erro;
     * (b) data vazia ({@code de=}) é o mesmo que ausente; (c) {@code ate} anterior a {@code de} é recusado (400).
     */
    private static void ajustarParametros(Operation operacao) {
        if (operacao.getParameters() == null) {
            return;
        }
        List<Parameter> ajustados = new ArrayList<>();
        boolean temDe = operacao.getParameters().stream().anyMatch(p -> "de".equals(p.getName()));
        for (Parameter parametro : operacao.getParameters()) {
            if ("pageable".equals(parametro.getName())) {
                ajustados.add(new Parameter().name("page").in("query").required(false)
                        .description("Página, a partir de 0. Valor inválido (negativo) usa a página 0.")
                        .schema(new Schema<Integer>().type("integer")));
                ajustados.add(new Parameter().name("size").in("query").required(false)
                        .description("Itens por página. Valor menor que 1 usa o padrão do endereço.")
                        .schema(new Schema<Integer>().type("integer")));
                ajustados.add(new Parameter().name("sort").in("query").required(false)
                        .description("Ordenação: campo,asc ou campo,desc. Campo desconhecido é recusado (400).")
                        .schema(new Schema<String>().type("array").items(new Schema<String>().type("string"))));
                continue;
            }
            Schema<?> esquema = parametro.getSchema();
            if (esquema != null && "date".equals(esquema.getFormat())) {
                parametro.setAllowEmptyValue(true);
                if (temDe && "ate".equals(parametro.getName())) {
                    parametro.setDescription("Data final. Não pode ser anterior a 'de' (400, 'Período inválido' ou parâmetros inválidos).");
                }
            }
            ajustados.add(parametro);
        }
        operacao.setParameters(ajustados);
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
