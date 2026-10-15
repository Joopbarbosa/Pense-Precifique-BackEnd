package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.17.0 — #719 (RN-NOVA-12 e 17). Cenários CEN-NOVO-38, 39, 40 e 54 (parte da lista) mais formas de pagamento e produtos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardVendasIT {

    @Autowired DashboardVendasService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoItemRepository itensOrcamento;
    @Autowired VendaCaixaRepository vendas;
    @Autowired VendaCaixaItemRepository itensCaixa;
    @Autowired CaixaTurnoRepository turnos;

    private Usuario usuario;
    private Cliente cliente;
    private Produto produto;
    private CaixaTurno turno;
    private static final LocalDate OUT = LocalDate.of(2026, 10, 1);
    private static final LocalDate OUT_FIM = LocalDate.of(2026, 10, 31);

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("dash-vendas-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        produto = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Laço de fita").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("15.00")).estoqueAtual(BigDecimal.ZERO).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(OUT.atStartOfDay()).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.ABERTO).build());
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Orcamento orcamento(LocalDate dia, StatusOrcamento status, String total, int numero) {
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numero).status(status)
                .dataEntrega(status == StatusOrcamento.ENTREGUE ? dia.atTime(12, 0) : null).createdAt(dia.atTime(9, 0))
                .subtotal(bd(total)).total(bd(total)).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(produto).quantidade(1).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(bd("1")).build());
        return o;
    }

    private VendaCaixa caixa(LocalDate dia, StatusVendaCaixa status, String total, int numero) {
        VendaCaixa v = vendas.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numero).status(status).caixaTurno(turno)
                .dataVenda(dia.atTime(10, 0)).subtotal(bd(total)).total(bd(total)).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(produto).quantidade(BigDecimal.ONE).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(bd("1")).build());
        return v;
    }

    private void cenarioOutubro() {
        caixa(OUT.plusDays(9), StatusVendaCaixa.CONCLUIDA, "45", 2);
        orcamento(OUT.plusDays(19), StatusOrcamento.ENTREGUE, "100", 14);
        caixa(LocalDate.of(2026, 9, 15), StatusVendaCaixa.CONCLUIDA, "100", 1);
    }

    private void cenarioTodosOsStatus() {
        cenarioOutubro();
        caixa(OUT.plusDays(10), StatusVendaCaixa.CANCELADA, "30", 3);
        orcamento(OUT.plusDays(11), StatusOrcamento.RASCUNHO, "80", 15);
        orcamento(OUT.plusDays(12), StatusOrcamento.CANCELADO, "60", 16);
    }

    @Test
    void cen38_numerosEComparativo() {
        cenarioOutubro();

        DashboardVendasResponse d = service.dashboard(OUT, OUT_FIM);

        assertEquals(0, bd("145").compareTo(d.faturamento().valor()));
        assertEquals(0, bd("45.00").compareTo(d.faturamento().variacaoPercentual()));
        assertEquals(0, bd("2").compareTo(d.quantidadeVendas().valor()));
        assertEquals(0, bd("72.50").compareTo(d.ticketMedio().valor()));
    }

    @Test
    void cen39_listaMostraTodosOsStatusMasSoVendasContamNosNumeros() {
        cenarioTodosOsStatus();

        ListaVendasResponse todas = service.lista(OUT, OUT_FIM, null, null, null, false, PageRequest.of(0, 20));
        assertEquals(5, todas.quantidadeFiltrada());
        DashboardVendasResponse d = service.dashboard(OUT, OUT_FIM);
        assertEquals(0, bd("145").compareTo(d.faturamento().valor()));
        assertEquals(0, bd("2").compareTo(d.quantidadeVendas().valor()));

        ListaVendasResponse canceladas = service.lista(OUT, OUT_FIM, null, List.of("CANCELADO", "CANCELADA"), null, false, PageRequest.of(0, 20));
        assertEquals(List.of("CX-3", "ORC-16"), canceladas.vendas().getContent().stream()
                .map(ListaVendasResponse.Linha::identificador).sorted().toList());
    }

    @Test
    void cen40_cliqueNaFatiaDeOrigemAbreAListaFiltradaEAmpliarMostraOsOutrosStatus() {
        cenarioTodosOsStatus();
        DashboardVendasResponse d = service.dashboard(OUT, OUT_FIM);
        BigDecimal fatiaOrcamento = d.porOrigem().stream().filter(o -> o.origem().equals("ORCAMENTO")).findFirst().orElseThrow().faturamento();
        assertEquals(0, bd("100").compareTo(fatiaOrcamento));

        ListaVendasResponse filtrada = service.lista(OUT, OUT_FIM, TipoVendaCmv.ORCAMENTO, null, null, true, PageRequest.of(0, 20));
        assertEquals(List.of("ORC-14"), filtrada.vendas().getContent().stream().map(ListaVendasResponse.Linha::identificador).toList());
        assertEquals(0, fatiaOrcamento.compareTo(filtrada.totalFiltrado()));

        ListaVendasResponse ampliada = service.lista(OUT, OUT_FIM, TipoVendaCmv.ORCAMENTO, null, null, false, PageRequest.of(0, 20));
        assertEquals(3, ampliada.quantidadeFiltrada());
    }

    @Test
    void cen54_listaAbertaPorNumeroDeVendasEPorTicketMedioConfereComOConjuntoInteiro() {
        cenarioOutubro();
        DashboardVendasResponse d = service.dashboard(OUT, OUT_FIM);

        // página de 1 linha: a conferência usa o conjunto inteiro filtrado, não só a página
        ListaVendasResponse lista = service.lista(OUT, OUT_FIM, null, null, null, true, PageRequest.of(0, 1));

        assertEquals(2, lista.quantidadeFiltrada());
        assertEquals(0, d.quantidadeVendas().valor().compareTo(BigDecimal.valueOf(lista.quantidadeFiltrada())));
        assertEquals(0, d.ticketMedio().valor().compareTo(lista.totalFiltrado().divide(BigDecimal.valueOf(lista.quantidadeFiltrada()), 2, java.math.RoundingMode.HALF_UP)));
        assertEquals(0, d.faturamento().valor().compareTo(lista.totalFiltrado()));
    }

    @Test
    void formasDePagamentoEProdutosMaisVendidos() {
        cenarioOutubro();

        DashboardVendasResponse d = service.dashboard(OUT, OUT_FIM);

        // orçamento usa a forma registrada (Pix, padrão); venda do Caixa sem pagamento registrado entra como "Não informada"
        assertEquals(0, bd("100").compareTo(d.formasPagamento().stream().filter(f -> f.forma().equals("Pix")).findFirst().orElseThrow().valor()));
        assertEquals(0, bd("45").compareTo(d.formasPagamento().stream().filter(f -> f.forma().equals("Não informada")).findFirst().orElseThrow().valor()));
        assertEquals("Laço de fita", d.produtosMaisVendidos().get(0).nome());
        assertEquals(0, bd("2").compareTo(d.produtosMaisVendidos().get(0).quantidade()));
        assertEquals(0, bd("145").compareTo(d.produtosMaisVendidos().get(0).faturamento()));
    }

    @Test
    void cliqueNoRankingDeProdutoEDeFormaAbreAsVendasQueFormamOValor() {
        cenarioTodosOsStatus();

        ListaVendasResponse porProduto = service.lista(OUT, OUT_FIM, null, null, null, true, produto.getId().toString(), null, PageRequest.of(0, 20));
        assertEquals(2, porProduto.quantidadeFiltrada());
        assertEquals(0, bd("145").compareTo(porProduto.totalFiltrado()));

        ListaVendasResponse porForma = service.lista(OUT, OUT_FIM, null, null, null, true, null, "Pix", PageRequest.of(0, 20));
        assertEquals(List.of("ORC-14"), porForma.vendas().getContent().stream().map(ListaVendasResponse.Linha::identificador).toList());
        ListaVendasResponse semForma = service.lista(OUT, OUT_FIM, null, null, null, true, null, "Não informada", PageRequest.of(0, 20));
        assertEquals(List.of("CX-2"), semForma.vendas().getContent().stream().map(ListaVendasResponse.Linha::identificador).toList());
    }

    @Test
    void buscaPorIdentificadorOuClienteESemAcentoEEntradaHostil() {
        cenarioTodosOsStatus();

        assertEquals(1, service.lista(OUT, OUT_FIM, null, null, "orc-14", false, PageRequest.of(0, 20)).quantidadeFiltrada());
        assertEquals(5, service.lista(OUT, OUT_FIM, null, null, "ANA", false, PageRequest.of(0, 20)).quantidadeFiltrada());
        assertThrows(RuntimeException.class, () -> service.dashboard(OUT_FIM, OUT));
        assertThrows(RuntimeException.class, () -> service.lista(OUT, OUT_FIM, null, null, null, false, PageRequest.of(0, 101)));
        assertThrows(RuntimeException.class, () -> service.dashboard(LocalDate.of(1900, 1, 1), OUT_FIM));
    }
}
