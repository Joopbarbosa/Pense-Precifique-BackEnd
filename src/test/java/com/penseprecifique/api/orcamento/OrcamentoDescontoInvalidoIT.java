package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RN-NOVA-8 (V0.8.2) — desconto maior que o subtotal (percentual ou em valor) e desconto negativo
 * passam a ser rejeitados na criação do orçamento, em vez do piso zero silencioso anterior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoDescontoInvalidoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-desconto-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Desconto").ativa(true).build());
    }

    private Produto novoProduto(int numero, BigDecimal precoVenda) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true)
                .precoVenda(precoVenda).build());
    }

    private OrcamentoRequest novoRequest(Produto produto, BigDecimal precoUnitario, String tipoDesconto,
                                          BigDecimal descontoValor) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("0"));
        item.setPrecoUnitario(precoUnitario);
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setTipoDesconto(tipoDesconto);
        req.setDescontoValor(descontoValor);
        return req;
    }

    @Test
    void descontoEmValorMaiorQueSubtotalRejeita() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("137.50"));
        OrcamentoRequest req = novoRequest(produto, new BigDecimal("137.50"), "VALOR", new BigDecimal("200.00"));

        assertThrows(BusinessException.class, () -> orcamentoService.criar(req));
    }

    @Test
    void descontoPercentualAcimaDeCemPorCentoRejeita() {
        seedUsuarioECliente();
        Produto produto = novoProduto(2, new BigDecimal("89.90"));
        OrcamentoRequest req = novoRequest(produto, new BigDecimal("89.90"), "PERCENTUAL", new BigDecimal("150"));

        assertThrows(BusinessException.class, () -> orcamentoService.criar(req));
    }

    @Test
    void descontoNegativoRejeita() {
        seedUsuarioECliente();
        Produto produto = novoProduto(3, new BigDecimal("240.00"));
        OrcamentoRequest req = novoRequest(produto, new BigDecimal("240.00"), "VALOR", new BigDecimal("-50.00"));

        assertThrows(BusinessException.class, () -> orcamentoService.criar(req));
    }

    @Test
    void descontoValidoDentroDoLimiteContinuaFuncionando() {
        seedUsuarioECliente();
        Produto produto = novoProduto(4, new BigDecimal("300.00"));
        OrcamentoRequest req = novoRequest(produto, new BigDecimal("300.00"), "PERCENTUAL", new BigDecimal("10"));

        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(0, new BigDecimal("270.00").compareTo(resultado.getTotal()));
    }
}
