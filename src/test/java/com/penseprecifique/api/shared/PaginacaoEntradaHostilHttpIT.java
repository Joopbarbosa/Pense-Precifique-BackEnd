package com.penseprecifique.api.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V0.17.0 #994 (RN-NOVA-31, DT-NOVA-27 item 6): entrada hostil das consultas dos 13 pontos que passam a ter paginação
 * numerada. Cada consulta recebe tamanho de página fora de 1 a 100, página negativa ou não numérica, ordenação fora da
 * lista permitida e busca com mais de 255 caracteres: a resposta é sempre 400 (nunca 500). Página além da última devolve
 * lista vazia com o total certo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaginacaoEntradaHostilHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired InsumoRepository insumos;
    @Autowired ProdutoRepository produtos;
    @Autowired UnidadeMedidaRepository unidades;
    private final ObjectMapper json = new ObjectMapper();

    private String token;
    private UUID clienteId;
    private UUID insumoId;
    private UUID produtoId;

    /** Uma consulta dos 13 pontos: rótulo para o relatório, endereço e se aceita ordenação do cliente. */
    private record Consulta(String ponto, String caminho, boolean aceitaOrdenacao) {}

    private List<Consulta> consultas() {
        return List.of(
                new Consulta("1 Insumos (lista e adicionar insumos à lista de compras)", "/insumos", true),
                new Consulta("2 Produtos (lista e busca de produtos do Caixa/orçamento)", "/produtos", true),
                new Consulta("3 Clientes e fornecedores", "/clientes", true),
                new Consulta("4 Orçamentos", "/orcamentos", true),
                new Consulta("5 Produção", "/producoes", true),
                new Consulta("6 Compras", "/compras", true),
                new Consulta("7 Listas de compras", "/listas-compra", false),
                new Consulta("8 Histórico do Detalhe do Insumo", "/insumos/" + insumoId + "/movimentacoes", false),
                new Consulta("9 Histórico do Detalhe do Produto", "/produtos/" + produtoId + "/movimentacoes", false),
                new Consulta("10a Histórico do Detalhe do Cliente (pedidos)", "/clientes/" + clienteId + "/historico/pedidos", false),
                new Consulta("10b Histórico do Detalhe do Fornecedor (compras)", "/clientes/" + clienteId + "/historico/compras", false),
                new Consulta("11a Modal de listagem do cadastro (registros)", "/clientes/" + clienteId + "/registros?papel=CLIENTE", false),
                new Consulta("11b Modal de listagem dos dashboards (vendas)", "/dashboard/vendas/lista", true),
                new Consulta("13a Busca de itens do orçamento", "/orcamentos/itens-catalogo", false),
                new Consulta("13b Busca de itens do Caixa", "/caixa/busca-itens-catalogo", false));
    }

    @BeforeEach
    void preparar() throws Exception {
        String email = "pag-hostil-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        token = n.get("token").asText();
        Usuario usuario = usuarios.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        clienteId = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria").ehCliente(true).ehFornecedor(true).ativa(true).build()).getId();
        UnidadeMedida un = unidades.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), "un")
                .orElseGet(() -> unidades.save(UnidadeMedida.builder().usuario(usuario).nome("un").sigla("un").build()));
        insumoId = insumos.save(Insumo.builder().usuario(usuario).numero(1).nome("Papel").unidadeMedida(un)
                .custoUnitario(new BigDecimal("4.00")).estoqueAtual(new BigDecimal("10")).permitirEstoqueNegativo(true).fracionavel(true).build()).getId();
        produtoId = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Caderno").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).ativo(true).precoVenda(new BigDecimal("10.00")).build()).getId();
    }

    private int status(String caminho, String parametros) throws Exception {
        String separador = caminho.contains("?") ? "&" : "?";
        return mvc.perform(get(caminho + (parametros.isEmpty() ? "" : separador + parametros)).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private List<String> falhas(String parametros, int esperado) throws Exception {
        List<String> falhas = new ArrayList<>();
        for (Consulta c : consultas()) {
            int obtido = status(c.caminho(), parametros);
            if (obtido != esperado) falhas.add(c.ponto() + " → " + obtido + " (esperado " + esperado + ")");
        }
        return falhas;
    }

    private void tudo400(String parametros) throws Exception {
        List<String> falhas = falhas(parametros, 400);
        assertTrue(falhas.isEmpty(), "Parâmetros '" + parametros + "': " + falhas);
    }

    @Test
    void tamanhoDePaginaForaDeUmACemDa400NasConsultas() throws Exception {
        for (String tamanho : List.of("0", "101", "-1", "2000", "abc", "")) tudo400("size=" + tamanho);
    }

    @Test
    void paginaNegativaOuNaoNumericaDa400NasConsultas() throws Exception {
        for (String pagina : List.of("-1", "-100", "abc", "1.5", "")) tudo400("page=" + pagina);
    }

    @Test
    void buscaComMaisDe255CaracteresDa400NasConsultas() throws Exception {
        tudo400("busca=" + "a".repeat(256));
        List<String> falhas = new ArrayList<>();
        for (Consulta c : consultas()) {
            int obtido = status(c.caminho(), "busca=" + "a".repeat(255));
            if (obtido >= 400) falhas.add(c.ponto() + " → " + obtido);
        }
        assertTrue(falhas.isEmpty(), "255 caracteres é o limite aceito: " + falhas);
    }

    @Test
    void ordenacaoForaDaListaPermitidaDa400NuncaErroInterno() throws Exception {
        List<String> falhas = new ArrayList<>();
        for (Consulta c : consultas()) {
            for (String sort : List.of("campoInexistente,asc", "senhaHash,desc", "usuario.email", "nome;drop table x")) {
                int obtido = status(c.caminho(), "sort=" + sort.replace(";", "%3B").replace(" ", "%20"));
                if (obtido != 400) falhas.add(c.ponto() + " sort=" + sort + " → " + obtido);
            }
        }
        assertTrue(falhas.isEmpty(), "Ordenação fora da lista: " + falhas);
    }

    @Test
    void paginaAlemDaUltimaDevolveListaVaziaComOTotalCerto() throws Exception {
        List<String> falhas = new ArrayList<>();
        for (Consulta c : consultas()) {
            String separador = c.caminho().contains("?") ? "&" : "?";
            MvcResult r = mvc.perform(get(c.caminho() + separador + "page=999&size=20").header("Authorization", "Bearer " + token)).andReturn();
            if (r.getResponse().getStatus() != 200) {
                falhas.add(c.ponto() + " → " + r.getResponse().getStatus());
                continue;
            }
            JsonNode corpo = json.readTree(r.getResponse().getContentAsString());
            JsonNode conteudo = corpo.has("content") ? corpo.get("content") : corpo.path("vendas").path("content");
            if (conteudo.size() != 0) falhas.add(c.ponto() + " → página 999 trouxe " + conteudo.size() + " registros");
            // as três listas com um registro cadastrado pelo teste mantêm o total do filtro na página vazia
            if (List.of("/insumos", "/produtos", "/clientes").contains(c.caminho()) && corpo.path("totalElements").asInt() != 1) {
                falhas.add(c.ponto() + " → total na página vazia = " + corpo.path("totalElements").asInt() + " (esperado 1)");
            }
        }
        assertTrue(falhas.isEmpty(), "Página além da última: " + falhas);
    }

    @Test
    void entradaValidaContinuaFuncionando() throws Exception {
        assertEquals(200, status("/insumos", "page=0&size=100&sort=nome,asc&busca=pap"));
        assertEquals(200, status("/clientes", "page=0&size=1"));
    }
}
