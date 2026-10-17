package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V0.17.0 #971 (RN-NOVA-23, DT-NOVA-18): ordenação da lista de vendas pelos campos da tabela (paginada depois de
 * ordenar), ranking de produtos com 10 itens e lista completa para a lupa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardVendasOrdenacaoIT {

    @Autowired DashboardVendasService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoItemRepository itensOrcamento;
    @Autowired VendaCaixaRepository vendas;
    @Autowired CaixaTurnoRepository turnos;

    private static final LocalDate OUT = LocalDate.of(2026, 10, 1);
    private static final LocalDate OUT_FIM = LocalDate.of(2026, 10, 31);
    private Usuario usuario;
    private Cliente cliente;
    private CaixaTurno turno;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("dash-ord-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(OUT.atStartOfDay()).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.ABERTO).build());
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Produto produto(String nome, int numero) {
        return produtos.save(Produto.builder().usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("10.00")).estoqueAtual(BigDecimal.ZERO).build());
    }

    private void orcamento(LocalDate dia, int numero, Produto produto, String total) {
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numero)
                .status(StatusOrcamento.ENTREGUE).dataEntrega(dia.atTime(12, 0)).createdAt(dia.atTime(9, 0))
                .subtotal(bd(total)).total(bd(total)).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(produto).quantidade(1).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(bd("1")).build());
    }

    private void venda(LocalDate dia, int numero, String total, StatusVendaCaixa status) {
        vendas.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numero).status(status).caixaTurno(turno)
                .dataVenda(dia.atTime(10, 0)).subtotal(bd(total)).total(bd(total)).build());
    }

    /** CX-2 45 (dia 10), CX-3 30 cancelada (dia 11) e ORC-14 100 entregue (dia 20). */
    private void cenario() {
        Produto laco = produto("Laço", 1);
        venda(OUT.plusDays(9), 2, "45", StatusVendaCaixa.CONCLUIDA);
        venda(OUT.plusDays(10), 3, "30", StatusVendaCaixa.CANCELADA);
        orcamento(OUT.plusDays(19), 14, laco, "100");
    }

    private List<String> identificadores(Sort ordem) {
        return service.lista(OUT, OUT_FIM, null, null, null, false, PageRequest.of(0, 20, ordem)).vendas().getContent()
                .stream().map(ListaVendasResponse.Linha::identificador).toList();
    }

    @Test
    void ordenaPeloTotalNosDoisSentidos() {
        cenario();
        List<String> asc = identificadores(Sort.by("total").ascending());
        assertEquals(List.of("CX-3", "CX-2", "ORC-14"), asc);
        assertEquals(List.of("ORC-14", "CX-2", "CX-3"), identificadores(Sort.by("total").descending()));
    }

    @Test
    void semOrdenacaoPedidaAMaisRecenteVemPrimeiro() {
        cenario();
        assertEquals(List.of("ORC-14", "CX-3", "CX-2"), identificadores(Sort.unsorted()));
    }

    @Test
    void ordenaPeloIdentificadorPeloNumeroNaoPeloTexto() {
        cenario();
        // CX-2 vem antes de CX-3 e ORC-14 depois de ambos: prefixo e depois número
        assertEquals(List.of("CX-2", "CX-3", "ORC-14"), identificadores(Sort.by("identificador").ascending()));
    }

    @Test
    void campoForaDaListaViraBloqueio() {
        cenario();
        assertThrows(BusinessException.class, () -> identificadores(Sort.by("senhaHash")));
    }

    @Test
    void rankingMostraDezProdutosEALupaListaTodos() {
        for (int i = 1; i <= 12; i++) {
            Produto p = produto(String.format("Produto %02d", i), i);
            orcamento(OUT.plusDays(5), 100 + i, p, "10");
        }
        DashboardVendasResponse d = service.dashboard(OUT, OUT_FIM);
        assertEquals(10, d.produtosMaisVendidos().size());
        assertEquals(12, d.todosProdutosVendidos().size());
        // mesma quantidade: desempate pelo nome (Produto 01 primeiro), e o 11º e o 12º só aparecem na lupa
        assertEquals("Produto 01", d.produtosMaisVendidos().get(0).nome());
        assertEquals("Produto 11", d.todosProdutosVendidos().get(10).nome());
    }
}
