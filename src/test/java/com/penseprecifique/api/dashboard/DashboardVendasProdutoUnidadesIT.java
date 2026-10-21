package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V0.17.0 #988 (RN-NOVA-23, alteração; DT-NOVA-25; CEN-NOVO-90 e 100): a lista de vendas filtrada por um produto devolve a
 * soma das unidades desse produto, ao lado do número de vendas. Uma venda de 3 unidades é 1 venda e 3 unidades; numa venda
 * com mais de um produto, a venda inteira aparece (com o total dela) e as unidades somam só as do produto clicado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardVendasProdutoUnidadesIT {

    @Autowired DashboardVendasService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoItemRepository itens;

    private static final LocalDate OUT = LocalDate.of(2026, 10, 1);
    private static final LocalDate OUT_FIM = LocalDate.of(2026, 10, 31);
    private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_FIM = LocalDate.of(2026, 11, 30);

    private Usuario usuario;
    private Cliente cliente;
    private Produto caderno;
    private Produto laco;
    private int proximoNumero = 1;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("dash-un-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        caderno = produto("Caderno personalizado", 1, "45.00");
        laco = produto("Laço pequeno", 2, "15.00");
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Produto produto(String nome, int numero, String preco) {
        return produtos.save(Produto.builder().usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd(preco)).estoqueAtual(BigDecimal.ZERO).build());
    }

    /** Orçamento ENTREGUE (conta nos números) com uma linha por par {produto, quantidade}; total = soma dos subtotais. */
    private void venda(LocalDate dia, Object... produtoEQuantidade) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < produtoEQuantidade.length; i += 2) {
            Produto p = (Produto) produtoEQuantidade[i];
            total = total.add(p.getPrecoVenda().multiply(BigDecimal.valueOf((int) produtoEQuantidade[i + 1])));
        }
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(proximoNumero++)
                .status(StatusOrcamento.ENTREGUE).dataEntrega(dia.atTime(12, 0)).createdAt(dia.atTime(9, 0))
                .subtotal(total).total(total).build());
        for (int i = 0; i < produtoEQuantidade.length; i += 2) {
            Produto p = (Produto) produtoEQuantidade[i];
            int q = (int) produtoEQuantidade[i + 1];
            itens.save(OrcamentoItem.builder().orcamento(o).produto(p).quantidade(q).precoUnitario(p.getPrecoVenda())
                    .subtotal(p.getPrecoVenda().multiply(BigDecimal.valueOf(q))).custoMaterialUnitario(bd("1")).build());
        }
    }

    private ListaVendasResponse listaDoProduto(LocalDate de, LocalDate ate, Produto produto) {
        return service.lista(de, ate, null, null, null, true, produto == null ? null : produto.getId().toString(), null, PageRequest.of(0, 100));
    }

    @Test
    void dezVendasDe18UnidadesDoMesmoProdutoDaoDezVendas18UnidadesERs810() {
        int[] quantidades = {3, 3, 2, 2, 2, 2, 1, 1, 1, 1}; // 6 + 8 + 4 = 18 em 10 vendas
        for (int i = 0; i < quantidades.length; i++) venda(OUT.plusDays(i), caderno, quantidades[i]);

        ListaVendasResponse r = listaDoProduto(OUT, OUT_FIM, caderno);

        assertEquals(10, r.quantidadeFiltrada());
        assertEquals(0, bd("18").compareTo(r.unidadesDoProduto()));
        assertEquals(0, bd("810.00").compareTo(r.totalFiltrado()));
    }

    @Test
    void vendaComDoisProdutosAparecePorInteiroEAsUnidadesSomamSoOProdutoClicado() {
        venda(NOV, caderno, 2, laco, 1); // CX-9: 2 × caderno + 1 × laço = R$ 105,00

        ListaVendasResponse doCaderno = listaDoProduto(NOV, NOV_FIM, caderno);
        assertEquals(1, doCaderno.quantidadeFiltrada());
        assertEquals(0, bd("2").compareTo(doCaderno.unidadesDoProduto()));
        assertEquals(0, bd("105.00").compareTo(doCaderno.totalFiltrado())); // total da venda inteira

        ListaVendasResponse doLaco = listaDoProduto(NOV, NOV_FIM, laco);
        assertEquals(1, doLaco.quantidadeFiltrada());
        assertEquals(0, bd("1").compareTo(doLaco.unidadesDoProduto()));
        assertEquals(0, bd("105.00").compareTo(doLaco.totalFiltrado()));
    }

    @Test
    void semFiltroDeProdutoAsUnidadesVemNulas() {
        venda(OUT, caderno, 2);
        assertNull(listaDoProduto(OUT, OUT_FIM, null).unidadesDoProduto());
    }
}
