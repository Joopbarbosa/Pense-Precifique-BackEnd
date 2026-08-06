package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.ItemSemEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #194/RN-NOVA-5 — GET /orcamentos/{id}/itens-sem-estoque. Exemplo numérico do prompt: Item A
 * "Kit Convite" quantidade 10, estoque 3 (insuficiente, falta 7) e Item B "Laço Decorativo"
 * quantidade 5, estoque 20 (suficiente, omitido). Endpoint é só leitura — não cria produção.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoItensSemEstoqueIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-estoque-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Itens Sem Estoque").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).build());
    }

    private OrcamentoItemRequest itemAvulso(UUID produtoId, int quantidade, String preco) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produtoId);
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal(preco));
        item.setQuantidade(quantidade);
        return item;
    }

    @Test
    void itemComEstoqueInsuficienteApareceSuficienteOmitido() {
        seedUsuarioECliente();
        Produto kitConvite = novoProduto("Kit Convite", 1, new BigDecimal("3"));
        Produto lacoDecorativo = novoProduto("Laço Decorativo", 2, new BigDecimal("20"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(
                itemAvulso(kitConvite.getId(), 10, "10.00"),
                itemAvulso(lacoDecorativo.getId(), 5, "5.00")));

        OrcamentoDetalheResponse orcamento = orcamentoService.criar(req);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());

        assertEquals(1, semEstoque.size(), "só o item com estoque insuficiente deve aparecer");
        ItemSemEstoqueResponse item = semEstoque.get(0);
        assertEquals(kitConvite.getId(), item.getProdutoId());
        assertEquals(0, new BigDecimal("10").compareTo(item.getQuantidadeSolicitada()));
        assertEquals(0, new BigDecimal("3").compareTo(item.getEstoqueAtual()));
        assertEquals(0, new BigDecimal("7").compareTo(item.getQuantidadeFaltante()));
    }

    @Test
    void todosOsItensComEstoqueSuficienteRetornaVazio() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto("Produto Suficiente A", 1, new BigDecimal("100"));
        Produto produtoB = novoProduto("Produto Suficiente B", 2, new BigDecimal("100"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(
                itemAvulso(produtoA.getId(), 10, "10.00"),
                itemAvulso(produtoB.getId(), 5, "5.00")));

        OrcamentoDetalheResponse orcamento = orcamentoService.criar(req);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());
        assertTrue(semEstoque.isEmpty());
    }

    @Test
    void orcamentoSemItensRetornaVazioSemErro() {
        seedUsuarioECliente();
        Orcamento orcamento = orcamentoRepository.save(Orcamento.builder()
                .usuario(usuario).cliente(cliente).numero(999).build());

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());
        assertTrue(semEstoque.isEmpty());
    }
}
