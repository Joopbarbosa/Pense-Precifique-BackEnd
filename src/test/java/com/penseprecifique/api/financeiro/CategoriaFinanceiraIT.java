package com.penseprecifique.api.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** V0.19.0 (#1177, RN-NOVA-23, CEN-NOVO-27 e 55) — categorias de despesa: semente, nome único, bloqueios, lançamento e entrada hostil. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CategoriaFinanceiraIT {

    private static final String SENHA = "senha-valida-123";
    private static final String BASE = "/financeiro/categorias";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired SementeCategoriasService semente;
    @Autowired LancamentoFinanceiroService lancamentoService;
    @Autowired CategoriaFinanceiraService categoriaService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired Clock relogioBrasilia;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    private final ObjectMapper json = new ObjectMapper();

    private record Conta(String email, String token, Usuario usuario) {}

    private Conta conta() throws Exception {
        String email = "fin-cat-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        return new Conta(email, token, usuarioRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow());
    }

    private MvcResult chamar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Conta c, String corpo) throws Exception {
        b.header("Authorization", "Bearer " + c.token());
        if (corpo != null) b.contentType(MediaType.APPLICATION_JSON).content(corpo);
        return mvc.perform(b).andReturn();
    }

    private JsonNode lista(Conta c, boolean inativas) throws Exception {
        MvcResult r = chamar(get(BASE + "?incluirInativas=" + inativas), c, null);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String idPorNome(Conta c, String nome) throws Exception {
        for (JsonNode n : lista(c, true)) if (n.get("nome").asText().equals(nome)) return n.get("id").asText();
        throw new AssertionError("sem categoria " + nome);
    }

    private MvcResult criarCategoria(Conta c, String nome) throws Exception {
        return chamar(post(BASE), c, "{\"nome\":" + json.writeValueAsString(nome) + "}");
    }

    /** Nome da categoria do lançamento ({@code null} se não tiver); lê dentro de uma transação por causa do carregamento tardio. */
    private String nomeDaCategoria(UUID lancamentoId) {
        return new org.springframework.transaction.support.TransactionTemplate(tm).execute(st -> {
            Lancamento l = lancamentoRepository.findById(lancamentoId).orElseThrow();
            return l.getCategoria() == null ? null : l.getCategoria().getNome();
        });
    }

    private void autenticar(Conta c) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(c.email(), null, List.of()));
    }

    private NovoLancamento despesa(OrigemLancamento origem, UUID categoriaId) {
        return new NovoLancamento(TipoLancamento.A_PAGAR, "Conta", origem, null, null,
                DivisaoParcelas.dividir(new BigDecimal("10.00"), 1, LocalDate.now(relogioBrasilia).plusDays(5)), categoriaId);
    }

    // ------------------------------------------------------------------------------------------ semente

    @Test
    void primeiraListagemSemeiaAsSeisPadraoEPodeRepetir() throws Exception {
        Conta c = conta();
        JsonNode l = lista(c, false);
        assertEquals(6, l.size());
        List<String> nomes = new ArrayList<>();
        l.forEach(n -> { nomes.add(n.get("nome").asText()); assertTrue(n.get("semeada").asBoolean()); assertTrue(n.get("ativa").asBoolean()); });
        assertTrue(nomes.containsAll(List.of("Insumos e materiais", "Embalagem", "Frete", "Aluguel", "Contas (água, luz, internet)", "Outros")));
        assertEquals(6, lista(c, false).size());
        assertEquals(6, categoriaRepository.findByUsuarioIdOrderBySemeadaDescNomeAsc(c.usuario().getId()).size());
    }

    @Test
    void semeaduraSimultaneaNaoDuplica() throws Exception {
        Conta c = conta();
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> { largada.await(); semente.garantir(c.usuario().getId()); return null; }));
        }
        largada.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(6, categoriaRepository.findByUsuarioIdOrderBySemeadaDescNomeAsc(c.usuario().getId()).size());
    }

    @Test
    void cadaContaTemASuaSemente() throws Exception {
        Conta a = conta();
        Conta b = conta();
        lista(a, false);
        assertEquals(0, categoriaRepository.findByUsuarioIdOrderBySemeadaDescNomeAsc(b.usuario().getId()).size());
        assertNotEquals(idPorNome(a, "Frete"), idPorNome(b, "Frete"));
    }

    // ------------------------------------------------------------------------------------------ nome

    @Test
    void criaCategoriaEBloqueiaNomeRepetidoSemDiferenciarMaiusculaAcentoEEspaco() throws Exception {
        Conta c = conta();
        MvcResult ok = criarCategoria(c, "  Café do ateliê ");
        assertEquals(201, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
        JsonNode criada = json.readTree(ok.getResponse().getContentAsString());
        assertEquals("Café do ateliê", criada.get("nome").asText());
        assertFalse(criada.get("semeada").asBoolean());
        for (String igual : new String[] {"café do ateliê", "CAFE DO ATELIE", " cafe do atelie  ", "Café do Ateliê"}) {
            MvcResult r = criarCategoria(c, igual);
            assertEquals(400, r.getResponse().getStatus(), igual);
            assertTrue(r.getResponse().getContentAsString().contains("Já existe uma categoria com esse nome."), r.getResponse().getContentAsString());
        }
        // nome igual ao de uma semeada também é repetido
        assertEquals(400, criarCategoria(c, "FRETE").getResponse().getStatus());
        assertEquals(400, criarCategoria(c, "insumos E materiais").getResponse().getStatus());
        // outra conta pode usar o mesmo nome
        assertEquals(201, criarCategoria(conta(), "Café do ateliê").getResponse().getStatus());
    }

    @Test
    void tamanhoDoNomeDe2a40() throws Exception {
        Conta c = conta();
        assertEquals(400, criarCategoria(c, "A").getResponse().getStatus());
        assertEquals(400, criarCategoria(c, "  B  ").getResponse().getStatus());
        assertEquals(201, criarCategoria(c, "Ab").getResponse().getStatus());
        assertEquals(201, criarCategoria(c, "x".repeat(40)).getResponse().getStatus());
        assertEquals(400, criarCategoria(c, "y".repeat(41)).getResponse().getStatus());
    }

    // ------------------------------------------------------------------------------------------ bloqueios

    @Test
    void semeadaNaoRenomeiaNaoExcluiNaoInativa() throws Exception {
        Conta c = conta();
        lista(c, false);
        String id = idPorNome(c, "Frete");
        MvcResult ren = chamar(put(BASE + "/" + id), c, "{\"nome\":\"Fretes\"}");
        assertEquals(400, ren.getResponse().getStatus());
        assertTrue(ren.getResponse().getContentAsString().contains("não pode ser renomeada"));
        MvcResult exc = chamar(delete(BASE + "/" + id), c, null);
        assertEquals(400, exc.getResponse().getStatus());
        assertTrue(exc.getResponse().getContentAsString().contains("não pode ser excluída"));
        assertEquals(400, chamar(post(BASE + "/" + id + "/inativar"), c, null).getResponse().getStatus());
        assertEquals("Frete", json.readTree(chamar(get(BASE), c, null).getResponse().getContentAsString()).findValuesAsText("nome").stream()
                .filter("Frete"::equals).findFirst().orElseThrow());
    }

    @Test
    void criadaRenomeiaInativaReativaMasNaoExclui() throws Exception {
        Conta c = conta();
        String id = json.readTree(criarCategoria(c, "Taxas").getResponse().getContentAsString()).get("id").asText();
        MvcResult ren = chamar(put(BASE + "/" + id), c, "{\"nome\":\"Taxas e tarifas\"}");
        assertEquals(200, ren.getResponse().getStatus(), ren.getResponse().getContentAsString());
        assertEquals(400, chamar(put(BASE + "/" + id), c, "{\"nome\":\"frete\"}").getResponse().getStatus());
        assertEquals(200, chamar(put(BASE + "/" + id), c, "{\"nome\":\"TAXAS E TARIFAS\"}").getResponse().getStatus(), "renomear para o próprio nome é permitido");
        MvcResult exc = chamar(delete(BASE + "/" + id), c, null);
        assertEquals(400, exc.getResponse().getStatus());
        assertTrue(exc.getResponse().getContentAsString().contains("Inative-a"));
        assertEquals(200, chamar(post(BASE + "/" + id + "/inativar"), c, null).getResponse().getStatus());
        assertEquals(6, lista(c, false).size());
        assertEquals(7, lista(c, true).size());
        // inativa continua ocupando o nome
        assertEquals(400, criarCategoria(c, "taxas e tarifas").getResponse().getStatus());
        assertEquals(200, chamar(post(BASE + "/" + id + "/reativar"), c, null).getResponse().getStatus());
        assertEquals(7, lista(c, false).size());
    }

    // ------------------------------------------------------------------------------------------ lançamento

    @Test
    void despesaAvulsaSemCategoriaFicaEmOutrosEReceitaFicaSem() throws Exception {
        Conta c = conta();
        autenticar(c);
        Lancamento l = lancamentoService.criar(despesa(OrigemLancamento.AVULSO, null));
        assertEquals("Outros", nomeDaCategoria(l.getId()));
        Lancamento r = lancamentoService.criar(new NovoLancamento(TipoLancamento.A_RECEBER, "Venda", OrigemLancamento.AVULSO, null, null,
                DivisaoParcelas.dividir(new BigDecimal("10.00"), 1, LocalDate.now(relogioBrasilia).plusDays(5))));
        assertNull(nomeDaCategoria(r.getId()));
    }

    @Test
    void despesaComCategoriaEscolhidaEBloqueios() throws Exception {
        Conta c = conta();
        lista(c, false);
        UUID frete = UUID.fromString(idPorNome(c, "Frete"));
        autenticar(c);
        Lancamento l = lancamentoService.criar(despesa(OrigemLancamento.AVULSO, frete));
        assertEquals("Frete", nomeDaCategoria(l.getId()));
        // receita com categoria
        BusinessException rec = assertThrows(BusinessException.class, () -> lancamentoService.criar(new NovoLancamento(
                TipoLancamento.A_RECEBER, "Venda", OrigemLancamento.AVULSO, null, null,
                DivisaoParcelas.dividir(new BigDecimal("10.00"), 1, LocalDate.now(relogioBrasilia).plusDays(5)), frete)));
        assertEquals("Receita não tem categoria.", rec.getMessage());
        // categoria inativa
        String criada = json.readTree(criarCategoria(c, "Taxas").getResponse().getContentAsString()).get("id").asText();
        chamar(post(BASE + "/" + criada + "/inativar"), c, null);
        autenticar(c);
        assertThrows(BusinessException.class, () -> lancamentoService.criar(despesa(OrigemLancamento.AVULSO, UUID.fromString(criada))));
        // despesa de orçamento sem categoria: não há padrão
        BusinessException sem = assertThrows(BusinessException.class,
                () -> categoriaService.resolverParaLancamento(c.usuario().getId(), TipoLancamento.A_PAGAR, OrigemLancamento.ORCAMENTO, null));
        assertEquals("Informe a categoria da despesa.", sem.getMessage());
    }

    @Test
    void despesaDeCompraUsaInsumosEMateriais() throws Exception {
        Conta c = conta();
        UUID id = c.usuario().getId();
        assertEquals("Insumos e materiais", categoriaService.resolverParaLancamento(id, TipoLancamento.A_PAGAR, OrigemLancamento.COMPRA, null).getNome());
        UUID outros = UUID.fromString(idPorNome(c, "Outros"));
        assertThrows(BusinessException.class, () -> categoriaService.resolverParaLancamento(id, TipoLancamento.A_PAGAR, OrigemLancamento.COMPRA, outros));
        UUID insumos = UUID.fromString(idPorNome(c, "Insumos e materiais"));
        assertEquals(insumos, categoriaService.resolverParaLancamento(id, TipoLancamento.A_PAGAR, OrigemLancamento.COMPRA, insumos).getId());
    }

    @Test
    void categoriaDeOutraContaDa404IgualAIdInexistente() throws Exception {
        Conta dona = conta();
        Conta intrusa = conta();
        lista(dona, false);
        lista(intrusa, false);
        String idDona = idPorNome(dona, "Frete");
        String inexistente = UUID.randomUUID().toString();
        // renomear / inativar / reativar / excluir: mesma resposta para id alheio e inexistente
        String[][] chamadas = {{"put", "{\"nome\":\"Qualquer\"}", ""}, {"post", null, "/inativar"}, {"post", null, "/reativar"}, {"delete", null, ""}};
        for (String[] ch : chamadas) {
            MvcResult alheio = chamar(montar(ch[0], BASE + "/" + idDona + ch[2]), intrusa, ch[1]);
            MvcResult nada = chamar(montar(ch[0], BASE + "/" + inexistente + ch[2]), intrusa, ch[1]);
            assertEquals(404, alheio.getResponse().getStatus(), ch[0] + ch[2]);
            assertEquals(nada.getResponse().getStatus(), alheio.getResponse().getStatus());
            assertEquals(semHora(nada), semHora(alheio), ch[0] + ch[2]);
        }
        // lançamento com categoria alheia x inexistente
        autenticar(intrusa);
        ResourceNotFoundException alheia = assertThrows(ResourceNotFoundException.class,
                () -> lancamentoService.criar(despesa(OrigemLancamento.AVULSO, UUID.fromString(idDona))));
        ResourceNotFoundException nenhuma = assertThrows(ResourceNotFoundException.class,
                () -> lancamentoService.criar(despesa(OrigemLancamento.AVULSO, UUID.fromString(inexistente))));
        assertEquals(nenhuma.getMessage(), alheia.getMessage());
        // a categoria da dona não mudou
        assertEquals("Frete", categoriaRepository.findById(UUID.fromString(idDona)).orElseThrow().getNome());
    }

    private static String semHora(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder montar(String metodo, String url) {
        return switch (metodo) {
            case "put" -> put(url);
            case "delete" -> delete(url);
            default -> post(url);
        };
    }

    // ------------------------------------------------------------------------------------------ entrada hostil

    @Test
    void entradaHostilNuncaDa500() throws Exception {
        Conta c = conta();
        String[] corpos = {
                "{}", "{\"nome\":null}", "{\"nome\":123}", "{\"nome\":true}", "{\"nome\":[]}", "{\"nome\":{}}", "{\"nome\":\"\"}", "{\"nome\":\"   \"}",
                "{\"nome\":\"abc\\u0000def\"}", "{\"nome\":\"\\u0000\"}", "{\"nome\":\"" + "a".repeat(501) + "\"}", "{\"nome\":\"" + "a".repeat(100001) + "\"}",
                "{\"nome\":\"linha\\nquebrada\"}", "{\"nome\":\"1e999\"}", "{\"nome\":\"'; drop table categorias_financeiras;--\"}",
                "{", "[]", "null", "\"texto\"", "", "{\"nome\":\"x\",\"nome\":\"y\"}",
        };
        for (String corpo : corpos) {
            int s = chamar(post(BASE), c, corpo).getResponse().getStatus();
            assertTrue(s == 400 || s == 201, corpo.substring(0, Math.min(60, corpo.length())) + " -> " + s);
        }
        int tipoErrado = mvc.perform(post(BASE).header("Authorization", "Bearer " + c.token()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andReturn().getResponse().getStatus();
        assertTrue(tipoErrado == 415 || tipoErrado == 400, "content-type: " + tipoErrado);
        int semTipo = mvc.perform(post(BASE).header("Authorization", "Bearer " + c.token())).andReturn().getResponse().getStatus();
        assertTrue(semTipo == 415 || semTipo == 400, "sem content-type: " + semTipo);
        assertEquals(400, criarCategoria(c, "a".repeat(100001)).getResponse().getStatus());
        assertEquals(400, criarCategoria(c, "abc\u0000def").getResponse().getStatus());
        // ids ruins e consultas ruins
        for (String ruim : new String[] {"nao-uuid", "123", "%00", "-1", "a".repeat(5000)}) {
            for (String sufixo : new String[] {"", "/inativar", "/reativar"}) {
                int s = chamar(sufixo.isEmpty() ? put(BASE + "/" + ruim) : post(BASE + "/" + ruim + sufixo), c, sufixo.isEmpty() ? "{\"nome\":\"zz\"}" : null)
                        .getResponse().getStatus();
                assertTrue(s == 400 || s == 404 || s == 405, ruim.substring(0, Math.min(10, ruim.length())) + sufixo + " -> " + s);
            }
            int d = chamar(delete(BASE + "/" + ruim), c, null).getResponse().getStatus();
            assertTrue(d == 400 || d == 404 || d == 405, "delete " + d);
        }
        for (String q : new String[] {"?incluirInativas=abc", "?incluirInativas=", "?incluirInativas=" + "a".repeat(50000), "?x=%00"}) {
            int s = chamar(get(BASE + q), c, null).getResponse().getStatus();
            assertTrue(s >= 200 && s < 500, q.substring(0, Math.min(40, q.length())) + " -> " + s);
        }
        int semToken = mvc.perform(get(BASE)).andReturn().getResponse().getStatus();
        assertTrue(semToken == 401 || semToken == 403);
    }

    @Test
    void nomeNormalizadoIgnoraAcentoCaixaEEspacos() {
        assertEquals("cafe do atelie", NomeCategoria.normalizar("  Café do Ateliê "));
        assertEquals(NomeCategoria.normalizar("Ação"), NomeCategoria.normalizar("ACAO"));
        assertEquals("contas (agua, luz, internet)", NomeCategoria.normalizar("Contas (água, luz, internet)"));
    }
}
