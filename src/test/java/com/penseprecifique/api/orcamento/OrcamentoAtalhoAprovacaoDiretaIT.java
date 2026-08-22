package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P-B006/RN-NOVA-2/RN-NOVA-3 (V0.8.2) — RN-NOVA-3 substitui a obrigatoriedade incondicional de
 * ORC-018 por prazo condicional a {@code temPrazoProducao}; RN-NOVA-2 é o atalho ENVIADO→FINALIZADO
 * (sinal inativo + sem prazo de produção + estoque suficiente pula
 * AGUARDANDO_SINAL/SINAL_PAGO/EM_PRODUCAO), exceção deliberada ao invariante de fluxo linear de
 * ORC-005. Casos 1/2 cobrem RN-NOVA-3; Casos 3-7 cobrem RN-NOVA-2 (elegibilidade + reaproveitamento
 * da confirmação de estoque negativo já usada em EM_PRODUCAO→FINALIZADO, ver
 * OrcamentoRn052EstoqueNegativoIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoAtalhoAprovacaoDiretaIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-atalho-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Atalho").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private OrcamentoRequest montarRequest(Produto produto, int quantidade, boolean sinalAtivo,
                                            boolean temPrazoProducao, Integer prazoProducaoDias) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(temPrazoProducao);
        req.setPrazoProducaoDias(prazoProducaoDias);
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setPercentualSinal(new BigDecimal("30"));
        }
        req.setItens(List.of(item));
        return req;
    }

    private UUID criarEEnviar(OrcamentoRequest req) {
        UUID id = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(id, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        return id;
    }

    @Test
    void criarSemPrazoAceitaComPrazoNulo() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sem Prazo", 1, new BigDecimal("100"), true);

        OrcamentoDetalheResponse resposta = orcamentoService.criar(montarRequest(produto, 1, false, false, null));

        assertNull(resposta.getPrazoProducaoDias());
    }

    @Test
    void criarComPrazoSimSemDiasRejeitaCom400() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Com Prazo", 2, new BigDecimal("100"), true);

        assertThrows(BusinessException.class,
                () -> orcamentoService.criar(montarRequest(produto, 1, false, true, null)));
    }

    @Test
    void atalhoCompletoPulaDiretoParaFinalizado() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Atalho", 3, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
        assertNotNull(detalhe.getDataAprovacao(), "ORC-019 — data de aprovação registrada mesmo pulando o status APROVADO persistido");

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("90").compareTo(atualizado.getEstoqueAtual()));
    }

    @Test
    void atalhoNaoHabilitadoPorSinalAtivoSegueFluxoNormal() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sinal", 4, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, true, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(inalterado.getEstoqueAtual()), "atalho não deve baixar estoque");
    }

    @Test
    void atalhoNaoHabilitadoPorTerPrazoSegueFluxoNormal() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Prazo", 5, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, true, 5));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());
    }

    @Test
    void atalhoNaoHabilitadoPorEstoqueInsuficienteSegueFluxoNormalSemErro() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sem Estoque", 6, new BigDecimal("2"), false);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(inalterado.getEstoqueAtual()));
    }

    @Test
    void atalhoComEstoqueNegativoReaproveitaConfirmacaoExistente() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Negativo Permitido", 7, new BigDecimal("3"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(produto.getId(), aviso.getAvisos().get(0).getComponenteId());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()), "não deve ter baixado sem confirmação");

        AvancaStatusRequest confirmar = new AvancaStatusRequest();
        confirmar.setConfirmarEstoqueNegativoProdutoIds(List.of(produto.getId()));
        Object resultadoConfirmado = orcamentoService.avancarStatus(id, confirmar);

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultadoConfirmado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("-7").compareTo(atualizado.getEstoqueAtual()));
    }
}
