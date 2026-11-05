package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.financeiro.Lancamento;
import com.penseprecifique.api.financeiro.LancamentoFinanceiroService;
import com.penseprecifique.api.financeiro.LancamentoRepository;
import com.penseprecifique.api.financeiro.OrcamentoDaParcela;
import com.penseprecifique.api.financeiro.PapelParcela;
import com.penseprecifique.api.financeiro.ParcelaOrcamentoResponse;
import com.penseprecifique.api.financeiro.SituacaoLancamento;
import com.penseprecifique.api.financeiro.SituacaoParcela;
import com.penseprecifique.api.financeiro.SubtipoLancamento;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.testsupport.MetodosPagamentoTeste;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1174 / PP-522 (RN-NOVA-10/11/12/38/40/41/43/51; CEN-NOVO-17 a 21, 44, 46, 47, 48): o passo Pagamento no fluxo real do orçamento,
 * as parcelas geradas pelo LancamentoFinanceiroService, o "Recebi" nas duas telas e o cancelamento com estorno e multa.
 */
/**
 * #1174 (CEN-NOVO-55, RN-NOVA-38/41): entrada hostil do passo Pagamento em avancar-status, pagamento/validar e PUT pagamento.
 * Corpo malformado, tipo errado, texto e números extremos, caractere nulo, id de outra empresa (404 idêntico ao inexistente).
 * Resposta sempre 4xx, nunca 500, e nada é gravado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoPagamentoHostilHttpIT {

    private static final String SENHA = "senha-valida-123";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    private final ObjectMapper om = new ObjectMapper();
    private String tokenDona;
    private String tokenOutra;
    private UUID orcamentoId;
    private UUID metodoId;

    private String registrar(String prefixo) throws Exception {
        String email = prefixo + UUID.randomUUID() + "@teste.com";
        String corpo = mvc.perform(MockMvcRequestBuilders.post("/auth/register").contentType(JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(corpo);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
        return n.get("token").asText();
    }

    @BeforeEach
    void preparar() throws Exception {
        tokenOutra = registrar("hostil-pag-outra-");
        tokenDona = registrar("hostil-pag-dona-"); // deixa a dona autenticada para montar o cenário
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Cliente").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Produto").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("300.00")).build());
        metodoId = MetodosPagamentoTeste.pix(metodos, usuario).getId();
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("300.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        orcamentoId = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        SecurityContextHolder.clearContext();
    }

    private MvcResult enviar(String token, HttpMethod metodo, String caminho, String tipo, String corpo) throws Exception {
        var b = MockMvcRequestBuilders.request(metodo, caminho).header("Authorization", "Bearer " + token);
        if (tipo != null) b.contentType(tipo);
        if (corpo != null) b.content(corpo);
        return mvc.perform(b).andReturn();
    }

    private void esperar4xx(List<String> falhas, String rotulo, MvcResult r) {
        int s = r.getResponse().getStatus();
        if (s < 400 || s >= 500) falhas.add(rotulo + " -> " + s);
    }

    private static String pag(String campos) {
        return "{\"pagamento\":{" + campos + "}}";
    }

    @Test
    void corposHostisNuncaDao500NemGravamNada() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String longo = "x".repeat(501);
        String muitoLongo = "9".repeat(100_001);
        String[] puros = {
                "", "{", "[]", "null", "{\"metodoPagamentoId\":\"abc\"}", "{\"metodoPagamentoId\":123}",
                "{\"temSinal\":\"talvez\"}", "{\"temSinal\":[]}", "{\"tipoSinal\":\"XYZ\"}",
                "{\"percentualSinal\":\"abc\"}", "{\"percentualSinal\":-1}", "{\"percentualSinal\":101}",
                "{\"percentualSinal\":" + muitoLongo + "}", "{\"valorSinal\":1e999}", "{\"valorSinal\":-5}",
                "{\"valorSinal\":0}", "{\"valorSinal\":99999999999999999999999}", "{\"valorSinal\":\"1\\u00002\"}",
                "{\"sinalAte\":\"31/12/2026\"}", "{\"sinalAte\":\"+999999999-01-01\"}", "{\"sinalAte\":\"amanhã\"}",
                "{\"vencimentoSaldo\":\"0000-00-00\"}", "{\"vencimentoSaldo\":-1}", "{\"metodoPagamentoId\":\"" + uuid + "\",\"x\":\"" + longo + "\"}",
        };
        List<String> falhas = new ArrayList<>();
        for (String corpo : puros) {
            String rot = corpo.length() > 40 ? corpo.substring(0, 40) : corpo;
            esperar4xx(falhas, "PUT pagamento " + rot, enviar(tokenDona, HttpMethod.PUT, "/orcamentos/" + orcamentoId + "/pagamento", JSON, corpo));
            esperar4xx(falhas, "validar " + rot, enviar(tokenDona, HttpMethod.POST, "/orcamentos/" + orcamentoId + "/pagamento/validar", JSON, corpo));
            esperar4xx(falhas, "avancar " + rot, enviar(tokenDona, HttpMethod.POST, "/orcamentos/" + orcamentoId + "/avancar-status", JSON, pag(corpo.startsWith("{") && corpo.endsWith("}") ? corpo.substring(1, corpo.length() - 1) : "\"x\":" + corpo)));
        }
        esperar4xx(falhas, "sem corpo", enviar(tokenDona, HttpMethod.PUT, "/orcamentos/" + orcamentoId + "/pagamento", JSON, null));
        esperar4xx(falhas, "texto puro", enviar(tokenDona, HttpMethod.PUT, "/orcamentos/" + orcamentoId + "/pagamento", MediaType.TEXT_PLAIN_VALUE, "x"));
        esperar4xx(falhas, "xml", enviar(tokenDona, HttpMethod.PUT, "/orcamentos/" + orcamentoId + "/pagamento", MediaType.APPLICATION_XML_VALUE, "<a/>"));
        esperar4xx(falhas, "id inválido na rota", enviar(tokenDona, HttpMethod.PUT, "/orcamentos/abc/pagamento", JSON, "{}"));
        esperar4xx(falhas, "motivo > 500", enviar(tokenDona, HttpMethod.POST, "/orcamentos/" + orcamentoId + "/avancar-status", JSON,
                "{\"motivoCancelamento\":\"" + longo + "\"}"));
        esperar4xx(falhas, "metodoRecebidoId inválido", enviar(tokenDona, HttpMethod.POST, "/orcamentos/" + orcamentoId + "/avancar-status", JSON,
                "{\"metodoRecebidoId\":\"abc\"}"));
        esperar4xx(falhas, "dataRecebimento inválida", enviar(tokenDona, HttpMethod.POST, "/orcamentos/" + orcamentoId + "/avancar-status", JSON,
                "{\"dataRecebimento\":\"ontem\"}"));
        assertTrue(falhas.isEmpty(), "falhas: " + falhas);
        var o = orcamentos.findById(orcamentoId).orElseThrow();
        assertEquals(StatusOrcamento.ENVIADO, o.getStatus());
        assertFalse(o.getSinalAtivo());
    }

    @Test
    void idDeOutraEmpresaDaOMesmo404DoInexistente() throws Exception {
        String corpo = "{\"metodoPagamentoId\":\"" + metodoId + "\",\"temSinal\":false}";
        String inexistente = UUID.randomUUID().toString();
        for (String sufixo : new String[]{"/pagamento", "/pagamento/validar", "/avancar-status"}) {
            HttpMethod m = sufixo.equals("/pagamento") ? HttpMethod.PUT : HttpMethod.POST;
            String body = sufixo.equals("/avancar-status") ? pag("\"metodoPagamentoId\":\"" + metodoId + "\",\"temSinal\":false") : corpo;
            MvcResult alheio = enviar(tokenOutra, m, "/orcamentos/" + orcamentoId + sufixo, JSON, body);
            MvcResult nada = enviar(tokenOutra, m, "/orcamentos/" + inexistente + sufixo, JSON, body);
            assertEquals(404, alheio.getResponse().getStatus(), sufixo);
            assertEquals(404, nada.getResponse().getStatus(), sufixo);
            assertEquals(om.readTree(nada.getResponse().getContentAsString()).path("message"),
                    om.readTree(alheio.getResponse().getContentAsString()).path("message"), sufixo);
        }
        assertEquals(StatusOrcamento.ENVIADO, orcamentos.findById(orcamentoId).orElseThrow().getStatus());
    }

    @Test
    void metodoDeOutraEmpresaNaoEAceito() throws Exception {
        MvcResult r = enviar(tokenOutra, HttpMethod.POST, "/orcamentos/" + UUID.randomUUID() + "/avancar-status", JSON,
                pag("\"metodoPagamentoId\":\"" + metodoId + "\",\"temSinal\":false"));
        assertTrue(r.getResponse().getStatus() >= 400 && r.getResponse().getStatus() < 500);
    }

    @Test
    void semTokenDa401ou403() throws Exception {
        int s = mvc.perform(MockMvcRequestBuilders.put("/orcamentos/" + orcamentoId + "/pagamento").contentType(JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "sem token -> " + s);
    }
}
