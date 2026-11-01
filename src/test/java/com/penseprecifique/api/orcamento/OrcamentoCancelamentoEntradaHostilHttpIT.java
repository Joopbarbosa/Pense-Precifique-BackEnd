package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.testsupport.AprovacaoTeste;
import com.penseprecifique.api.testsupport.MetodosPagamentoTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PP-692 (RN-NOVA-50, RN-NOVA-52; CEN-NOVO-76, 80, 81, 87) — multa do cancelamento e taxa da maquininha pela API,
 * com entrada hostil: valor fora do limite, texto, notação científica, vazio, corpo malformado, content-type errado
 * e id de outra conta. Nunca 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoCancelamentoEntradaHostilHttpIT {

    @Autowired MockMvc mockMvc;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private Usuario usuario;
    private String token;
    private UUID orcamentoId;

    private void preparar() {
        usuario = usuarioRepository.save(Usuario.builder().email("orc-hostil-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        token = jwtTokenProvider.generateToken(usuario);
        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("C").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("P").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("1000.00")).build());
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("1000.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(true);
        req.setValorSinal(new BigDecimal("100.00"));
        orcamentoId = orcamentoService.criar(req).getId();
        AprovacaoTeste.avancar(orcamentoService, orcamentoId, new AvancaStatusRequest());
        AprovacaoTeste.avancar(orcamentoService, orcamentoId, new AvancaStatusRequest());
        AvancaStatusRequest sinal = new AvancaStatusRequest();
        sinal.setMetodoSinalRecebidoId(MetodosPagamentoTeste.pix(metodos, usuario).getId());
        AprovacaoTeste.avancar(orcamentoService, orcamentoId, sinal);
        AprovacaoTeste.avancar(orcamentoService, orcamentoId, sinal);
        assertEquals("SINAL_PAGO", statusAtual());
    }

    private String statusAtual() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        return orcamentoService.buscarPorId(orcamentoId).getStatus().name();
    }

    private org.springframework.test.web.servlet.ResultActions cancelar(String corpo) throws Exception {
        return mockMvc.perform(post("/orcamentos/" + orcamentoId + "/cancelar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private void assertNaoFoi500(String corpo) throws Exception {
        int s = cancelar(corpo).andReturn().getResponse().getStatus();
        assertTrue(s >= 400 && s < 500, "esperado 4xx para " + corpo + " mas foi " + s);
        assertEquals("SINAL_PAGO", statusAtual());
    }

    @Test
    void multaZeroOuNegativaERecusadaComAMensagemDaRegra() throws Exception {
        preparar();
        for (String v : List.of("0", "0.00", "-1", "0.001", "-0.01")) {
            cancelar("{\"percentualMulta\":" + v + "}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.percentualMulta").exists());
        }
        cancelar("{\"percentualMulta\":0}")
                .andExpect(jsonPath("$.fieldErrors.percentualMulta").value("Informe uma multa maior que zero ou escolha cancelar sem multa."));
        assertEquals("SINAL_PAGO", statusAtual());
    }

    @Test
    void multaAcimaDoTotalERecusadaEIgualAoTotalPassaDaValidacao() throws Exception {
        preparar();
        cancelar("{\"percentualMulta\":100.01}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.percentualMulta").value("A multa não pode ser maior que o total do orçamento."));
        cancelar("{\"percentualMulta\":101}").andExpect(status().isBadRequest());
        // SINAL_PAGO não cobra multa por percentual; com 100 a validação passa e o cancelamento segue (CEN-88 completo no IT de serviço)
        cancelar("{\"percentualMulta\":100,\"motivoCancelamento\":\"Desistencia\"}").andExpect(status().isOk());
    }

    @Test
    void entradaHostilNuncaDa500() throws Exception {
        preparar();
        for (String corpo : List.of(
                "{\"percentualMulta\":1e20}", "{\"percentualMulta\":1e999}", "{\"percentualMulta\":-1e999}",
                "{\"percentualMulta\":\"abc\"}", "{\"percentualMulta\":[1]}",
                "{\"percentualMulta\":{}}", "{\"percentualMulta\":50.123}", "{\"percentualMulta\":true}",
                "{\"percentualMulta\":99999999999999999999999999999999}",
                "{\"estornarValorRecebido\":\"talvez\"}", "{\"devolverEstoque\":5}",
                "{\"percentualMulta\":", "[]", "\"texto\"", "{\"percentualMulta\":NaN}")) {
            assertNaoFoi500(corpo);
        }
        int s = mockMvc.perform(post("/orcamentos/" + orcamentoId + "/cancelar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.TEXT_PLAIN).content("percentualMulta=50")).andReturn().getResponse().getStatus();
        assertTrue(s >= 400 && s < 500, "content-type errado: " + s);
    }

    @Test
    void cancelarOrcamentoDeOutraContaERecusadoSemVazarNada() throws Exception {
        preparar();
        Usuario outro = usuarioRepository.save(Usuario.builder().email("orc-hostil-o-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        String tokenOutro = jwtTokenProvider.generateToken(outro);
        int s = mockMvc.perform(post("/orcamentos/" + orcamentoId + "/cancelar").header("Authorization", "Bearer " + tokenOutro)
                .contentType(MediaType.APPLICATION_JSON).content("{\"percentualMulta\":50}")).andReturn().getResponse().getStatus();
        assertEquals(404, s);
        assertEquals("SINAL_PAGO", statusAtual());
    }

    // ---------------------------------------------------------------------------------------- taxa da maquininha (RN-NOVA-52)

    private MetodoPagamentoConfiguravel cartao(Usuario u) {
        return MetodosPagamentoTeste.criar(metodos, u, TipoMetodoPagamento.CARTAO_DEBITO, true);
    }

    private int salvarTaxa(UUID id, String taxa) throws Exception {
        return mockMvc.perform(put("/configuracoes/metodos-pagamento/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"taxaMaquininha\":" + taxa + "}")).andReturn().getResponse().getStatus();
    }

    @Test
    void taxaDaMaquininhaAceitaDeZeroACemERecusaOResto() throws Exception {
        preparar();
        MetodoPagamentoConfiguravel m = cartao(usuario);
        assertEquals(200, salvarTaxa(m.getId(), "2.99"));
        for (String ruim : List.of("100.01", "-1", "-0.01", "1000", "1e20", "1e999", "\"abc\"", "[1]", "{}", "2.999", "NaN")) {
            int s = salvarTaxa(m.getId(), ruim);
            assertTrue(s >= 400 && s < 500, "taxa " + ruim + " deveria dar 4xx mas deu " + s);
        }
        mockMvc.perform(put("/configuracoes/metodos-pagamento/" + m.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taxaMaquininha\":100.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.taxaMaquininha").value("A taxa deve estar entre 0% e 100%."));
        assertEquals(0, new BigDecimal("2.99").compareTo(metodos.findById(m.getId()).orElseThrow().getTaxaMaquininha()));
        assertEquals(200, salvarTaxa(m.getId(), "100"));
        assertEquals(0, new BigDecimal("100").compareTo(metodos.findById(m.getId()).orElseThrow().getTaxaMaquininha()));
        assertEquals(200, salvarTaxa(m.getId(), "0"));
    }
}
