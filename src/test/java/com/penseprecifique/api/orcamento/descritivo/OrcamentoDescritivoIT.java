package com.penseprecifique.api.orcamento.descritivo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.financeiro.LancamentoRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.pdf.OrcamentoDescritivoPdfPayloadService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * V0.19.0 #382 (RN-NOVA-44, UC-NOVO-5) — orçamento Descritivo: CEN-NOVO-49 (gerar Venda com preço atual), 50 (limites e
 * sem total/aprovação), 57 (cliente obrigatório só ao gerar a Venda), 22 (sem lançamento/parcela) e 55 (entrada hostil).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoDescritivoIT {

    private static final String SENHA = "senha-valida-123";
    private static final String BASE = "/orcamentos-descritivos";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired OrcamentoDescritivoPdfPayloadService pdfPayloadService;
    private final ObjectMapper json = new ObjectMapper();
    private int numero = 1;

    private record Conta(String token, Usuario usuario) {}

    private Conta conta() throws Exception {
        String email = "desc-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        return new Conta(token, usuarioRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow());
    }

    private Produto produto(Usuario u, String nome, String preco) {
        return produtoRepository.save(Produto.builder().usuario(u).numero(numero++).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal(preco)).build());
    }

    private Cliente cliente(Usuario u, String nome) {
        return clienteRepository.save(Cliente.builder().usuario(u).numero(numero++).nome(nome).ativa(true).build());
    }

    private MvcResult chamar(Conta c, MockHttpServletRequestBuilder b, String corpo) throws Exception {
        b.header("Authorization", "Bearer " + c.token());
        if (corpo != null) b.contentType(MediaType.APPLICATION_JSON).content(corpo);
        return mvc.perform(b).andReturn();
    }

    private JsonNode corpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String descritivoJson(String titulo, UUID clienteId, UUID... produtos) {
        StringBuilder s = new StringBuilder("{\"titulo\":\"" + titulo + "\",");
        if (clienteId != null) s.append("\"clienteId\":\"").append(clienteId).append("\",");
        s.append("\"opcoes\":[");
        for (int i = 0; i < produtos.length; i++) {
            if (i > 0) s.append(',');
            s.append("{\"produtoId\":\"").append(produtos[i]).append("\",\"quantidadeUnidade\":\"2 dúzias\"}");
        }
        return s.append("]}").toString();
    }

    private JsonNode criarOk(Conta c, String corpo) throws Exception {
        MvcResult r = chamar(c, post(BASE), corpo);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return corpo(r);
    }

    @Test
    void cen49_gerarVendaUsaPrecoAtualEODescritivoNaoMuda() throws Exception {
        Conta c = conta();
        Produto p = produto(c.usuario(), "Bolo", "50.00");
        JsonNode d = criarOk(c, descritivoJson("Festa", null, p.getId()));
        assertEquals("DESCRITIVO", d.get("tipo").asText());
        assertEquals("RASCUNHO", d.get("status").asText());
        assertEquals(0, new BigDecimal("50.00").compareTo(d.get("opcoes").get(0).get("precoReferencia").decimalValue()));
        assertFalse(d.has("total"));
        assertFalse(d.has("desconto"));

        p.setPrecoVenda(new BigDecimal("80.00"));
        produtoRepository.save(p);
        String opcaoId = d.get("opcoes").get(0).get("id").asText();
        Cliente cli = cliente(c.usuario(), "Maria");
        MvcResult r = chamar(c, post(BASE + "/" + d.get("id").asText() + "/gerar-venda"),
                "{\"clienteId\":\"" + cli.getId() + "\",\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":3}]}");
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode venda = corpo(r);
        assertEquals("RASCUNHO", venda.get("status").asText());
        assertEquals(0, new BigDecimal("240.00").compareTo(venda.get("total").decimalValue()));

        JsonNode depois = corpo(chamar(c, get(BASE + "/" + d.get("id").asText()), null));
        assertTrue(depois.get("clienteId").isNull());
        assertEquals(0, new BigDecimal("50.00").compareTo(depois.get("opcoes").get(0).get("precoReferencia").decimalValue()));
        assertEquals(0, new BigDecimal("80.00").compareTo(depois.get("opcoes").get(0).get("precoAtual").decimalValue()));
        assertEquals("RASCUNHO", depois.get("status").asText());
    }

    @Test
    void cen50_limitesDeOpcoesESemAprovacao() throws Exception {
        Conta c = conta();
        MvcResult zero = chamar(c, post(BASE), "{\"titulo\":\"X\",\"opcoes\":[]}");
        assertEquals(400, zero.getResponse().getStatus());
        assertTrue(zero.getResponse().getContentAsString().contains("Informe de 1 a 100 opções."));

        Produto p = produto(c.usuario(), "Doce", "5.00");
        UUID[] cem1 = new UUID[101];
        java.util.Arrays.fill(cem1, p.getId());
        MvcResult acima = chamar(c, post(BASE), descritivoJson("X", null, cem1));
        assertEquals(400, acima.getResponse().getStatus());
        assertTrue(acima.getResponse().getContentAsString().contains("Informe de 1 a 100 opções."));

        UUID[] cem = new UUID[100];
        java.util.Arrays.fill(cem, p.getId());
        JsonNode ok = criarOk(c, descritivoJson("Cem", null, cem));
        assertEquals(100, ok.get("opcoes").size());

        String id = ok.get("id").asText();
        MvcResult aprovar = chamar(c, patch(BASE + "/" + id + "/status"), "{\"status\":\"APROVADO\"}");
        assertEquals(400, aprovar.getResponse().getStatus());
        MvcResult enviar = chamar(c, patch(BASE + "/" + id + "/status"), "{\"status\":\"ENVIADO\"}");
        assertEquals(200, enviar.getResponse().getStatus());
        MvcResult editar = chamar(c, put(BASE + "/" + id), descritivoJson("Novo", null, p.getId()));
        assertEquals(400, editar.getResponse().getStatus());
    }

    @Test
    void cen57_clienteObrigatorioSoAoGerarVenda() throws Exception {
        Conta c = conta();
        Produto p = produto(c.usuario(), "Torta", "30.00");
        JsonNode d = criarOk(c, descritivoJson("Sem cliente", null, p.getId()));
        String id = d.get("id").asText();
        String opcaoId = d.get("opcoes").get(0).get("id").asText();
        long antes = orcamentoRepository.count();
        MvcResult bloqueio = chamar(c, post(BASE + "/" + id + "/gerar-venda"),
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1}]}");
        assertEquals(400, bloqueio.getResponse().getStatus());
        assertTrue(bloqueio.getResponse().getContentAsString().contains("Escolha o cliente para gerar o orçamento."));
        assertEquals(antes, orcamentoRepository.count());

        Cliente cli = cliente(c.usuario(), "Ana");
        MvcResult ok = chamar(c, post(BASE + "/" + id + "/gerar-venda"),
                "{\"clienteId\":\"" + cli.getId() + "\",\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1}]}");
        assertEquals(201, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
        assertEquals(antes + 1, orcamentoRepository.count());
    }

    @Test
    void cen22_semLancamentoNemParcelaEFicaForaDasListasDeVenda() throws Exception {
        Conta c = conta();
        Produto p = produto(c.usuario(), "Pão", "10.00");
        long lancamentos = lancamentoRepository.count();
        JsonNode d = criarOk(c, descritivoJson("Listas", null, p.getId()));
        chamar(c, patch(BASE + "/" + d.get("id").asText() + "/status"), "{\"status\":\"ENVIADO\"}");
        assertEquals(lancamentos, lancamentoRepository.count());

        JsonNode vendas = corpo(chamar(c, get("/orcamentos"), null));
        assertEquals(0, vendas.get("content").size());
        JsonNode busca = corpo(chamar(c, get("/orcamentos?busca=Listas"), null));
        assertEquals(0, busca.get("content").size());
        MvcResult porId = chamar(c, get("/orcamentos/" + d.get("id").asText()), null);
        assertEquals(404, porId.getResponse().getStatus());
        JsonNode lista = corpo(chamar(c, get(BASE + "?busca=Listas"), null));
        assertEquals(1, lista.get("content").size());
    }

    @Test
    void payloadDoPdfUsaNumeroSimplesESemTotal() throws Exception {
        Conta c = conta();
        Produto p = produto(c.usuario(), "Cupcake", "7.5");
        JsonNode d = criarOk(c, descritivoJson("Mesa", null, p.getId()));
        UUID id = UUID.fromString(d.get("id").asText());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(c.usuario().getEmail(), null, java.util.List.of()));
        assertTrue(pdfPayloadService.ehDescritivo(id));
        var payload = pdfPayloadService.montarPayload(id);
        String serial = json.writeValueAsString(payload);
        assertFalse(serial.contains("ORC-"));
        assertTrue(serial.contains("Cupcake"));
        assertTrue(serial.contains("R$ 7,50"));
        assertFalse(serial.toLowerCase().contains("total"));
    }

    @Test
    void cen55_entradaHostilaNuncaDa500() throws Exception {
        Conta c = conta();
        Conta outra = conta();
        Produto meu = produto(c.usuario(), "Meu", "9.00");
        Produto alheio = produto(outra.usuario(), "Alheio", "9.00");
        JsonNode dAlheio = criarOk(outra, descritivoJson("Dela", null, alheio.getId()));
        String idAlheio = dAlheio.get("id").asText();
        String opcaoAlheia = dAlheio.get("opcoes").get(0).get("id").asText();
        UUID inexistente = UUID.randomUUID();

        // id de outra conta = id inexistente (404 idêntico)
        MvcResult a = chamar(c, get(BASE + "/" + idAlheio), null);
        MvcResult b = chamar(c, get(BASE + "/" + inexistente), null);
        assertEquals(404, a.getResponse().getStatus());
        assertEquals(404, b.getResponse().getStatus());
        assertEquals(a.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""),
                b.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""));
        assertEquals(404, chamar(c, put(BASE + "/" + idAlheio), descritivoJson("x", null, meu.getId())).getResponse().getStatus());
        assertEquals(404, chamar(c, patch(BASE + "/" + idAlheio + "/status"), "{\"status\":\"CANCELADO\"}").getResponse().getStatus());
        assertEquals(404, chamar(c, post(BASE + "/" + idAlheio + "/gerar-venda"),
                "{\"itens\":[{\"opcaoId\":\"" + opcaoAlheia + "\",\"quantidade\":1}]}").getResponse().getStatus());

        // produto e cliente de outra conta
        assertEquals(404, chamar(c, post(BASE), descritivoJson("x", null, alheio.getId())).getResponse().getStatus());
        Cliente clienteAlheio = cliente(outra.usuario(), "Dela");
        assertEquals(404, chamar(c, post(BASE), descritivoJson("x", clienteAlheio.getId(), meu.getId())).getResponse().getStatus());

        String ok = descritivoJson("ok", null, meu.getId());
        String[] corpos = {
                "", "{", "[]", "null", "{\"titulo\":null,\"opcoes\":[]}", "{\"titulo\":\"\",\"opcoes\":[]}",
                "{\"titulo\":\"" + "a".repeat(201) + "\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\"}]}",
                "{\"titulo\":\"x\\u0000y\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\"}]}",
                "{\"titulo\":\"x\",\"opcoes\":null}",
                "{\"titulo\":\"x\",\"opcoes\":[null]}",
                "{\"titulo\":\"x\",\"opcoes\":[{}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"nao-e-uuid\"}]}",
                "{\"titulo\":\"x\",\"clienteId\":\"nao-e-uuid\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\"}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"precoReferencia\":-1}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"precoReferencia\":1.234}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"precoReferencia\":1e400}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"precoReferencia\":\"abc\"}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"precoReferencia\":999999999999}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"itemCatalogoId\":\"" + UUID.randomUUID() + "\"}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + meu.getId() + "\",\"quantidadeUnidade\":\"" + "q".repeat(501) + "\"}]}",
                "{\"titulo\":\"x\",\"opcoes\":[{\"produtoId\":\"" + inexistente + "\"}]}"
        };
        for (String corpo : corpos) {
            int st = chamar(c, post(BASE), corpo).getResponse().getStatus();
            assertTrue(st >= 400 && st < 500, "POST " + corpo + " -> " + st);
        }
        MvcResult tipo = mvc.perform(post(BASE).header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.TEXT_PLAIN).content(ok)).andReturn();
        assertEquals(415, tipo.getResponse().getStatus());

        JsonNode d = criarOk(c, ok);
        String id = d.get("id").asText();
        String opcaoId = d.get("opcoes").get(0).get("id").asText();
        String[] gerar = {
                "", "{", "{}", "{\"itens\":[]}", "{\"itens\":null}", "{\"itens\":[null]}", "{\"itens\":[{}]}",
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":0}]}",
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":-5}]}",
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":2147483648}]}",
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1.5}]}",
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":\"a\"}]}",
                "{\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1},{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1}]}",
                "{\"itens\":[{\"opcaoId\":\"" + inexistente + "\",\"quantidade\":1}]}",
                "{\"clienteId\":\"" + inexistente + "\",\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1}]}",
                "{\"clienteId\":\"" + clienteAlheio.getId() + "\",\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1}]}",
                "{\"clienteId\":\"zzz\",\"itens\":[{\"opcaoId\":\"" + opcaoId + "\",\"quantidade\":1}]}"
        };
        for (String corpo : gerar) {
            int st = chamar(c, post(BASE + "/" + id + "/gerar-venda"), corpo).getResponse().getStatus();
            assertTrue(st >= 400 && st < 500, "gerar-venda " + corpo + " -> " + st);
        }
        String[] status = {"", "{", "{}", "{\"status\":null}", "{\"status\":\"XYZ\"}", "{\"status\":\"RASCUNHO\"}", "{\"status\":\"CONCLUIDO\"}"};
        for (String corpo : status) {
            int st = chamar(c, patch(BASE + "/" + id + "/status"), corpo).getResponse().getStatus();
            assertTrue(st >= 400 && st < 500, "status " + corpo + " -> " + st);
        }
        for (String url : new String[]{BASE + "/nao-uuid", BASE + "?status=XYZ", BASE + "?busca=" + "z".repeat(201),
                BASE + "?page=-1", BASE + "?size=100000", BASE + "?page=99999999999", BASE + "?sort=campoInexistente,asc",
                BASE + "?busca=%00", BASE + "?busca=%25%27%22;--"}) {
            int st = chamar(c, get(url), null).getResponse().getStatus();
            assertTrue(st < 500, "GET " + url + " -> " + st);
        }
        int semToken = mvc.perform(get(BASE)).andReturn().getResponse().getStatus();
        assertTrue(semToken == 401 || semToken == 403);
        assertNotNull(id);
    }
}
