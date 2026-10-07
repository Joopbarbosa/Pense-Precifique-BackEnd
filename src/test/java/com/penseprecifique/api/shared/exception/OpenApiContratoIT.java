package com.penseprecifique.api.shared.exception;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #765 (triagem do Schemathesis da V0.16.0) — o OpenAPI descreve as respostas reais: erros padrão em toda operação,
 * campos que podem vir nulos no dashboard de compras e o mínimo de caracteres da justificativa de cancelamento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenApiContratoIT {

    @Autowired MockMvc mockMvc;

    private JsonNode docs() throws Exception {
        String corpo = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(corpo);
    }

    @Test
    void todaOperacaoDeclaraAsRespostasDeErroPadrao() throws Exception {
        JsonNode paths = docs().get("paths");
        int operacoes = 0;
        for (var caminho : paths.properties()) {
            for (var op : caminho.getValue().properties()) {
                if (!List.of("get", "post", "put", "patch", "delete").contains(op.getKey())) continue;
                operacoes++;
                JsonNode respostas = op.getValue().get("responses");
                for (String codigo : List.of("400", "401", "403", "404", "405", "415", "500")) {
                    assertTrue(respostas.has(codigo), caminho.getKey() + " " + op.getKey() + " sem resposta " + codigo);
                }
                assertEquals("#/components/schemas/ErrorResponseDTO",
                        respostas.get("500").get("content").get("application/json").get("schema").get("$ref").asString());
            }
        }
        assertTrue(operacoes > 100, "esperava mais de 100 operações, achou " + operacoes);
    }

    @Test
    void camposQuePodemVirNulosNoDashboardAceitamNulo() throws Exception {
        JsonNode schemas = docs().get("components").get("schemas");
        for (String campo : List.of("percentual", "percentualAnterior", "variacaoPercentual")) {
            assertAceitaNulo(schemas.get("Cmv").get("properties").get(campo), "Cmv." + campo);
        }
        assertAceitaNulo(schemas.get("Numero").get("properties").get("valor"), "Numero.valor");
        assertAceitaNulo(schemas.get("Numero").get("properties").get("variacaoPercentual"), "Numero.variacaoPercentual");
        assertAceitaNulo(schemas.get("Economia").get("properties").get("percentual"), "Economia.percentual");
    }

    @Test
    void justificativaDoCancelamentoDeclaraOMinimoDe30() throws Exception {
        JsonNode j = docs().get("components").get("schemas").get("CancelarProducaoRequest").get("properties").get("justificativa");
        assertEquals(30, j.get("minLength").asInt());
        assertEquals(500, j.get("maxLength").asInt());
    }

    @Test
    void erroPadraoDescreveOTimestampSemFusoEOsCamposOpcionais() throws Exception {
        JsonNode erro = docs().get("components").get("schemas").get("ErrorResponseDTO").get("properties");
        assertEquals("local-date-time", erro.get("timestamp").get("format").asString());
        for (String campo : List.of("fieldErrors", "titulo", "motivo", "comoResolver", "itens")) {
            assertAceitaNulo(erro.get(campo), "ErrorResponseDTO." + campo);
        }
    }

    @Test
    void maiorAumentoDoDashboardNaoMisturaRefComTipoNulo() throws Exception {
        JsonNode m = docs().get("components").get("schemas").get("DashboardComprasResponse").get("properties").get("maiorAumento");
        assertTrue(!m.has("$ref") && !m.has("type"), "ref e type na mesma propriedade: " + m);
        assertEquals(2, m.get("anyOf").size());
        assertAceitaNulo(m.get("anyOf").get(1), "maiorAumento");
    }

    /** #775 — o contrato diz o que a API faz: DELETE sem conteúdo é 204, datas locais não têm fuso, senha responde 200. */
    @Test
    void contratoDescreveOs204EAsDatasLocais() throws Exception {
        JsonNode docs = docs();
        JsonNode apagar = docs.get("paths").get("/unidades-medida/{id}").get("delete").get("responses");
        assertTrue(apagar.has("204") && !apagar.has("200"), "DELETE /unidades-medida/{id}: " + apagar.propertyNames());
        JsonNode senha = docs.get("paths").get("/usuarios/me/senha").get("put").get("responses");
        assertTrue(senha.has("200") && !senha.has("204"), "PUT /usuarios/me/senha: " + senha.propertyNames());
        JsonNode datas = docs.get("components").get("schemas").get("UnidadeMedidaResponseDTO").get("properties");
        assertEquals("local-date-time", datas.get("createdAt").get("format").asString());
        assertEquals("local-date-time", datas.get("updatedAt").get("format").asString());
    }

    /** #780 e #782 — consulta documentada como a API a trata: page/size reais, sem mínimos falsos; data vazia aceita. */
    @Test
    void parametrosDeConsultaSaoOsReaisEAceitamValorVazioNasDatas() throws Exception {
        JsonNode docs = docs();
        JsonNode lista = docs.get("paths").get("/orcamentos").get("get").get("parameters");
        List<String> nomes = new java.util.ArrayList<>();
        lista.forEach(p -> nomes.add(p.get("name").asString()));
        assertTrue(nomes.containsAll(List.of("page", "size", "sort")) && !nomes.contains("pageable"), nomes.toString());
        for (JsonNode p : lista) {
            if ("page".equals(p.get("name").asString()) || "size".equals(p.get("name").asString())) {
                assertTrue(!p.get("schema").has("minimum"), p.toString());
                assertTrue(!p.path("required").asBoolean(false), p.toString());
            }
        }
        for (JsonNode p : docs.get("paths").get("/compras/dashboard").get("get").get("parameters")) {
            assertTrue(p.get("allowEmptyValue").asBoolean(false), p.toString());
        }
        boolean achou = false;
        for (JsonNode p : docs.get("paths").get("/clientes/{id}/registros").get("get").get("parameters")) {
            if ("ate".equals(p.get("name").asString())) {
                assertTrue(p.get("description").asString().contains("anterior a 'de'"), p.toString());
                achou = true;
            }
        }
        assertTrue(achou);
    }

    /** #783, #786, #787, #788 e #789 — tipos de paginação, nulos opcionais, vazios, justificativa e 201 no contrato. */
    @Test
    void contratoDescreveTiposNulosVaziosJustificativaE201() throws Exception {
        JsonNode docs = docs();
        for (JsonNode p : docs.get("paths").get("/orcamentos").get("get").get("parameters")) {
            String nome = p.get("name").asString();
            if (nome.equals("page") || nome.equals("size")) {
                assertEquals("integer", p.get("schema").get("type").asString(), p.toString());
            }
            if (nome.equals("sort")) {
                assertEquals("array", p.get("schema").get("type").asString(), p.toString());
                assertEquals("string", p.get("schema").get("items").get("type").asString(), p.toString());
            }
        }
        JsonNode schemas = docs.get("components").get("schemas");
        for (String campo : List.of("custoTotalLote", "estoqueMinimo", "precoSugerido", "rendimento", "fotoUrl")) {
            assertAceitaNulo(schemas.get("ProdutoResponse").get("properties").get(campo), "ProdutoResponse." + campo);
        }
        for (String campo : List.of("estoqueMinimo", "rendimento", "descricao", "fotoUrl")) {
            assertAceitaNulo(schemas.get("ProdutoDetalheResponse").get("properties").get(campo), "ProdutoDetalheResponse." + campo);
        }
        assertAceitaNulo(schemas.get("ProdutoRequest").get("properties").get("estoqueMinimo"), "ProdutoRequest.estoqueMinimo");
        assertTrue(schemas.get("ProdutoRequest").get("properties").get("margemLucro").toString().contains("\"maxLength\":0"),
                "margemLucro deve aceitar texto vazio: " + schemas.get("ProdutoRequest").get("properties").get("margemLucro"));
        JsonNode j = schemas.get("AvancaStatusRequest").get("properties").get("justificativa");
        assertEquals(30, j.get("minLength").asInt());
        // #811 — ficha técnica é opcional no contrato, como a API aceita (ausente ou nula = vazia)
        JsonNode obrigatorios = schemas.get("ProdutoRequest").get("required");
        assertTrue(obrigatorios == null || !obrigatorios.toString().contains("fichaTecnica"), "required: " + obrigatorios);
        JsonNode criar = docs.get("paths").get("/produtos").get("post").get("responses");
        assertTrue(criar.has("201") && !criar.has("200"), criar.propertyNames().toString());
        for (var par : List.of(List.of("/insumos", "incluirInativos"), List.of("/compras", "insumoId"), List.of("/listas-compra/previa", "insumoIds"))) {
            boolean achou = false;
            for (JsonNode p : docs.get("paths").get(par.get(0)).get("get").get("parameters")) {
                if (par.get(1).equals(p.get("name").asString())) {
                    assertTrue(p.get("allowEmptyValue").asBoolean(false), par + " " + p);
                    achou = true;
                }
            }
            assertTrue(achou, par.toString());
        }
    }

    /** #803, #804 e #805 — mínimos de texto e política geral de entrada no contrato. */
    @Test
    void contratoDeclaraMinimosDeTextoEAPoliticaGeralDeEntrada() throws Exception {
        JsonNode docs = docs();
        JsonNode schemas = docs.get("components").get("schemas");
        for (String nome : List.of("TravarProducaoRequest", "AgruparProducoesRequest", "CancelarCompraRequest",
                "CancelarVendaCaixaRequestDTO", "BaixaManualInsumoRequestDTO", "BaixaManualProdutoRequest", "CaixaMovimentoRequestDTO")) {
            boolean tem30 = false;
            for (var p : schemas.get(nome).get("properties").properties()) {
                JsonNode min = p.getValue().get("minLength");
                tem30 |= min != null && min.asInt() == 30;
            }
            assertTrue(tem30, nome + " sem minLength 30");
        }
        JsonNode descricao = docs.get("info").get("description");
        assertTrue(descricao != null && descricao.asString().contains("Política de entrada JSON"), "info.description: " + descricao);
        JsonNode unidade = schemas.get("UnidadeMedidaRequestDTO");
        if (unidade != null && unidade.has("required") && unidade.get("properties").has("nome")) {
            assertEquals(1, unidade.get("properties").get("nome").get("minLength").asInt());
        }
    }

    /** #812 e #813 — itens nulos de lista de entrada e precisão decimal declarados no contrato. */
    @Test
    void contratoDeclaraItensNulosEPrecisaoDecimal() throws Exception {
        JsonNode schemas = docs().get("components").get("schemas");
        JsonNode itens = schemas.get("ProdutoRequest").get("properties").get("fichaTecnica").get("items");
        assertTrue(itens.toString().contains("\"null\""), "fichaTecnica.items: " + itens);
        JsonNode linha = schemas.get("Linha");
        assertTrue(linha != null && linha.get("properties").get("quantidade").toString().contains("\"multipleOf\":1.0E-4"),
                "Linha.quantidade: " + (linha == null ? null : linha.get("properties").get("quantidade")));
        assertTrue(schemas.get("CompraItemRequest").get("properties").get("precoTotal").toString().contains("\"multipleOf\":0.01"),
                "CompraItemRequest.precoTotal: " + schemas.get("CompraItemRequest").get("properties").get("precoTotal"));
    }

    private static void assertAceitaNulo(JsonNode propriedade, String nome) {
        String texto = propriedade.toString();
        assertTrue(texto.contains("\"null\"") || texto.contains("\"nullable\":true"), nome + " não aceita nulo: " + texto);
    }
}
