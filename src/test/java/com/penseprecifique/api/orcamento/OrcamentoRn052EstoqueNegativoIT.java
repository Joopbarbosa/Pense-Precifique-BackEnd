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
import com.penseprecifique.api.shared.dto.request.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.OrcamentoDetalheResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #136/RN-052 — avançar EM_PRODUCAO → FINALIZADO passa a ter o mesmo mecanismo de aviso+confirmação
 * já religado em Produção (iniciar()/retomar()): produto com permitirEstoqueNegativo=true cujo
 * resultado ficaria negativo e ainda não confirmado gera ConfirmacaoEstoqueNegativoResponse em vez
 * de baixar. RN-059 (permitirEstoqueNegativo=false) continua bloqueando incondicionalmente — o único
 * comportamento pré-existente neste fluxo, sem gate de confirmação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoRn052EstoqueNegativoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-rn052-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente RN-052").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .build());
    }

    private UUID criarOrcamentoAteEmProducao(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));

        UUID orcamentoId = orcamentoService.criar(req).getId();
        // RASCUNHO -> ENVIADO -> APROVADO -> EM_PRODUCAO (sinal inativo por padrão)
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        return orcamentoId;
    }

    @Test
    void finalizarSemConfirmacaoRetornaAvisoENaoBaixa() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), true);
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 10);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(produto.getId(), aviso.getAvisos().get(0).getComponenteId());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()), "não deve ter baixado sem confirmação");
    }

    @Test
    void finalizarComConfirmacaoBaixaENegativa() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), true);
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 10);

        AvancaStatusRequest request = new AvancaStatusRequest();
        request.setConfirmarEstoqueNegativoProdutoIds(List.of(produto.getId()));
        Object resultado = orcamentoService.avancarStatus(orcamentoId, request);

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("-7").compareTo(atualizado.getEstoqueAtual()), "confirmado, deve baixar e ficar negativo");
    }

    @Test
    void permitirEstoqueNegativoFalseContinuaBloqueandoSemGateDeConfirmacao() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), false);
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 10);

        AvancaStatusRequest request = new AvancaStatusRequest();
        request.setConfirmarEstoqueNegativoProdutoIds(List.of(produto.getId()));

        assertThrows(BusinessException.class, () -> orcamentoService.avancarStatus(orcamentoId, request));

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()));
    }

    @Test
    void estoqueSuficienteBaixaDiretoSemAviso() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("100"), true);
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 10);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("90").compareTo(atualizado.getEstoqueAtual()));
    }
}
