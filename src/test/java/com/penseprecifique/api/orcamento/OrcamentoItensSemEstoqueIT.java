package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #194/ORC-028 (rótulo antigo "RN-NOVA-5" — ver RN-NOVA-24) — GET /orcamentos/{id}/itens-sem-estoque.
 * Exemplo numérico do prompt original: Item A "Kit Convite" quantidade 10, estoque 3 (insuficiente,
 * falta 7) e Item B "Laço Decorativo" quantidade 5, estoque 20 (suficiente, omitido). Endpoint é só
 * leitura — não cria produção.
 *
 * RN-NOVA-26 (V0.8.3, #319+387) — testes adicionais cobrindo {@code producaoVinculadaId}/
 * {@code identificadorProducaoVinculada}: sem vínculo, vínculo não-terminal, vínculo terminal
 * (mesmo com linha em orcamento_producoes) e múltiplos vínculos históricos (só o não-terminal conta).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoItensSemEstoqueIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;

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
                .tempoProducao(30).estoqueAtual(estoqueAtual).precoVenda(new BigDecimal("10.00")).build());
    }

    private OrcamentoItemRequest itemAvulso(UUID produtoId, int quantidade, String preco) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produtoId);
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal(preco));
        item.setQuantidade(quantidade);
        return item;
    }

    /**
     * RN-NOVA-26 — cria uma produção no estado pedido, cobrindo {@code produto} (via
     * {@code ProducaoProduto}) e vinculada ao orçamento (via {@code OrcamentoProducao}), sem passar
     * pelos endpoints reais de criação (que exigem ficha técnica/rendimento) — aqui só o estado da
     * produção e sua cobertura de produto importam para o teste do badge.
     */
    private Producao criarProducaoVinculada(UUID orcamentoId, Produto produto, EstadoProducao estado, int numero) {
        Orcamento orcamento = orcamentoRepository.findById(orcamentoId).orElseThrow();
        Producao producao = producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(numero).estado(estado).build());
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produto).quantidade(new BigDecimal("1")).build());
        orcamentoProducaoRepository.save(OrcamentoProducao.builder()
                .orcamento(orcamento).producao(producao).build());
        return producao;
    }

    @Test
    void itemComEstoqueInsuficienteApareceSuficienteOmitido() {
        seedUsuarioECliente();
        Produto kitConvite = novoProduto("Kit Convite", 1, new BigDecimal("3"));
        Produto lacoDecorativo = novoProduto("Laço Decorativo", 2, new BigDecimal("20"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
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
        req.setTemPrazoProducao(true);
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

    /** RN-NOVA-26 — item sem estoque e sem nenhum vínculo de produção: badge não acende. */
    @Test
    void itemSemVinculoRetornaProducaoVinculadaIdNulo() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemAvulso(produto.getId(), 10, "10.00")));

        OrcamentoDetalheResponse orcamento = orcamentoService.criar(req);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());

        assertEquals(1, semEstoque.size());
        assertNull(semEstoque.get(0).getProducaoVinculadaId());
        assertNull(semEstoque.get(0).getIdentificadorProducaoVinculada());
    }

    /** RN-NOVA-26 — item com vínculo em estado não-terminal (EM_ANDAMENTO): badge acende. */
    @Test
    void itemComVinculoNaoTerminalRetornaProducaoVinculadaIdPreenchido() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemAvulso(produto.getId(), 10, "10.00")));

        OrcamentoDetalheResponse orcamento = orcamentoService.criar(req);
        Producao producaoVinculada = criarProducaoVinculada(orcamento.getId(), produto, EstadoProducao.EM_ANDAMENTO, 1);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());

        assertEquals(1, semEstoque.size());
        assertEquals(producaoVinculada.getId(), semEstoque.get(0).getProducaoVinculadaId());
        assertEquals("PRD-1", semEstoque.get(0).getIdentificadorProducaoVinculada());
    }

    /**
     * RN-NOVA-26 — item com vínculo já FINALIZADA (terminal): badge não acende, mesmo existindo a
     * linha em orcamento_producoes — mesmo comportamento de "nenhum vínculo" (2º caso do exemplo
     * numérico do prompt).
     */
    @Test
    void itemComVinculoTerminalRetornaProducaoVinculadaIdNulo() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemAvulso(produto.getId(), 10, "10.00")));

        OrcamentoDetalheResponse orcamento = orcamentoService.criar(req);
        criarProducaoVinculada(orcamento.getId(), produto, EstadoProducao.FINALIZADA, 1);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());

        assertEquals(1, semEstoque.size());
        assertNull(semEstoque.get(0).getProducaoVinculadaId());
        assertNull(semEstoque.get(0).getIdentificadorProducaoVinculada());
    }

    /**
     * RN-NOVA-26 — item com múltiplos vínculos históricos (um CANCELADA, outro EM_ANDAMENTO): só o
     * não-terminal conta, badge aponta para a produção ativa, não para a cancelada.
     */
    @Test
    void itemComMultiplosVinculosSoONaoTerminalConta() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemAvulso(produto.getId(), 10, "10.00")));

        OrcamentoDetalheResponse orcamento = orcamentoService.criar(req);
        criarProducaoVinculada(orcamento.getId(), produto, EstadoProducao.CANCELADA, 1);
        Producao producaoAtiva = criarProducaoVinculada(orcamento.getId(), produto, EstadoProducao.EM_ANDAMENTO, 2);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamento.getId());

        assertEquals(1, semEstoque.size());
        assertEquals(producaoAtiva.getId(), semEstoque.get(0).getProducaoVinculadaId());
        assertEquals("PRD-2", semEstoque.get(0).getIdentificadorProducaoVinculada());
    }
}
