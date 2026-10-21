package com.penseprecifique.api.produto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * V0.17.0 #1017 (RN-NOVA-36, CEN-NOVO-117 a 121 e entrada hostil): marca opcional no produto, nome + marca únicos por conta (sem maiúsculas,
 * acentos e espaços das pontas; inativos contam), cadastro antigo repetido segue editável, marca acima de 100 caracteres e tipo errado → 400,
 * criações simultâneas da mesma combinação → uma só salva.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MarcaProdutoHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtos;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "marca-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private static String corpo(String nome, String marcaJson, String extra) {
        return "{\"nome\":" + nome + ",\"tipo\":\"PRODUTO\",\"tempoProducao\":10,\"rendimento\":1,\"precoVenda\":10,\"fichaTecnica\":[]"
                + (marcaJson == null ? "" : ",\"marca\":" + marcaJson) + (extra == null ? "" : "," + extra) + "}";
    }

    private MvcResult criar(String t, String corpo) throws Exception {
        return mvc.perform(post("/produtos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private MvcResult editar(String t, String id, String corpo) throws Exception {
        return mvc.perform(put("/produtos/" + id).header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private JsonNode corpoDe(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }

    @Test
    void cen117ProdutoGuardaAMarcaSemEspacosNasPontas() throws Exception {
        String t = token();
        MvcResult r = criar(t, corpo("\"Caderno\"", "\"  Pense  \"", null));
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("Pense", corpoDe(r).get("marca").asText());
    }

    @Test
    void cen118MesmoNomeComMarcasDiferentesEAceito() throws Exception {
        String t = token();
        assertEquals(201, criar(t, corpo("\"Caderno\"", "\"Pense\"", null)).getResponse().getStatus());
        assertEquals(201, criar(t, corpo("\"Caderno\"", "\"Escolar\"", null)).getResponse().getStatus());
    }

    @Test
    void cen119MesmoNomeEMarcaSemDiferenciarMaiusculaAcentoEEspacoBloqueiaNoCampoNome() throws Exception {
        String t = token();
        assertEquals(201, criar(t, corpo("\"Caderno Ação\"", "\"Pense\"", null)).getResponse().getStatus());
        MvcResult r = criar(t, corpo("\" caderno acao \"", "\"PENSE\"", null));
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("Já existe um produto com este nome e esta marca", corpoDe(r).get("fieldErrors").get("nome").asText());
        // sem marca contra sem marca
        assertEquals(201, criar(t, corpo("\"Fita\"", null, null)).getResponse().getStatus());
        assertEquals(400, criar(t, corpo("\"fita\"", "\"  \"", null)).getResponse().getStatus());
    }

    /** #1020 — só caixa, acento e espaços das pontas são ignorados; espaço interno duplicado é outro nome. */
    @Test
    void espacoInternoDuplicadoNaoContaComoOMesmoNome() throws Exception {
        String t = token();
        assertEquals(201, criar(t, corpo("\"A B QA\"", "\"Pense\"", null)).getResponse().getStatus());
        assertEquals(201, criar(t, corpo("\"A  B QA\"", "\"Pense\"", null)).getResponse().getStatus());
        assertEquals(400, criar(t, corpo("\" a b qa \"", "\"PENSE\"", null)).getResponse().getStatus());
    }

    @Test
    void produtoInativoTambemContaNaUnicidade() throws Exception {
        String t = token();
        MvcResult r = criar(t, corpo("\"Caderno\"", "\"Pense\"", null));
        String id = corpoDe(r).get("id").asText();
        mvc.perform(post("/produtos/" + id + "/desativar").header("Authorization", "Bearer " + t)).andReturn();
        assertEquals(400, criar(t, corpo("\"Caderno\"", "\"Pense\"", null)).getResponse().getStatus());
    }

    @Test
    void cen120CadastroAntigoRepetidoEditaOutrosCamposMasNaoTrocaParaIdentidadeDeOutro() throws Exception {
        String t = token();
        String a = corpoDe(criar(t, corpo("\"Fita de cetim\"", null, null))).get("id").asText();
        // cadastro antigo repetido: grava direto o mesmo nome sem marca (a API já não deixaria)
        var original = produtos.findById(UUID.fromString(a)).orElseThrow();
        var copia = produtos.save(com.penseprecifique.api.shared.domain.entity.Produto.builder().usuario(original.getUsuario()).numero(original.getNumero() + 1)
                .nome("Fita de cetim").tipo(original.getTipo()).tempoProducao(10).precoVenda(original.getPrecoVenda()).rendimento(original.getRendimento())
                .estoqueAtual(original.getEstoqueAtual()).build());
        String b = copia.getId().toString();

        // editar só o preço dos dois: permitido
        assertEquals(200, editar(t, a, corpo("\"Fita de cetim\"", null, null).replace("\"precoVenda\":10", "\"precoVenda\":12")).getResponse().getStatus());
        assertEquals(200, editar(t, b, corpo("\"Fita de cetim\"", null, null).replace("\"precoVenda\":10", "\"precoVenda\":13")).getResponse().getStatus());
        // mudar a marca do segundo para "Luxo" é permitido; mudar a do primeiro para "Luxo" fica igual ao segundo e bloqueia no campo Marca
        assertEquals(200, editar(t, b, corpo("\"Fita de cetim\"", "\"Luxo\"", null)).getResponse().getStatus());
        MvcResult r = editar(t, a, corpo("\"Fita de cetim\"", "\"Luxo\"", null));
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("Já existe um produto com este nome e esta marca", corpoDe(r).get("fieldErrors").get("marca").asText());
    }

    @Test
    void cen121MarcaAcimaDe100CaracteresETipoErradoViramBloqueio() throws Exception {
        String t = token();
        MvcResult r = criar(t, corpo("\"Caderno\"", "\"" + "A".repeat(101) + "\"", null));
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("A marca deve ter no máximo 100 caracteres", corpoDe(r).get("fieldErrors").get("marca").asText());
        assertEquals(201, criar(t, corpo("\"Caderno\"", "\"" + "A".repeat(100) + "\"", null)).getResponse().getStatus());
        assertEquals(400, criar(t, corpo("\"Outro\"", "123", null)).getResponse().getStatus());
        assertEquals(400, criar(t, corpo("\"Outro\"", "false", null)).getResponse().getStatus());
        assertEquals(400, criar(t, corpo("\"Outro\"", "\"Ma\\u0000rca\"", null)).getResponse().getStatus());
    }

    @Test
    void naoVazaMarcaEntreContas() throws Exception {
        String t1 = token();
        String t2 = token();
        assertEquals(201, criar(t1, corpo("\"Caderno\"", "\"Pense\"", null)).getResponse().getStatus());
        assertEquals(201, criar(t2, corpo("\"Caderno\"", "\"Pense\"", null)).getResponse().getStatus());
    }

    @Test
    void buscaPorNomeTambemAchaPelaMarca() throws Exception {
        String t = token();
        criar(t, corpo("\"Caderno\"", "\"Escolar\"", null));
        criar(t, corpo("\"Caneta\"", "\"Pense\"", null));
        MvcResult r = mvc.perform(get("/produtos").param("busca", "escolar").header("Authorization", "Bearer " + t)).andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode lista = corpoDe(r);
        JsonNode itens = lista.has("content") ? lista.get("content") : lista.has("produtos") ? lista.get("produtos") : lista;
        assertNotNull(itens);
        assertEquals(1, itens.size());
        assertEquals("Caderno", itens.get(0).get("nome").asText());
        assertEquals("Escolar", itens.get(0).get("marca").asText());
    }

    @Test
    void duasCriacoesSimultaneasDaMesmaCombinacaoSalvamSoUma() throws Exception {
        String t = token();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Integer> tarefa = () -> {
            largada.await();
            return criar(t, corpo("\"Caderno\"", "\"Pense\"", null)).getResponse().getStatus();
        };
        Future<Integer> f1 = pool.submit(tarefa);
        Future<Integer> f2 = pool.submit(tarefa);
        largada.countDown();
        List<Integer> status = List.of(f1.get(), f2.get()).stream().sorted().toList();
        pool.shutdown();
        assertEquals(List.of(201, 400), status, "uma salva e a outra é bloqueada");
    }
}
