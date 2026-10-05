package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.*;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.*;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** #615 RN-NOVA-20/DT-NOVA-13: a lupa compartilha o universo e o cálculo do dashboard. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class VendasCmvIT {
    @Autowired DashboardCompraService dashboard;
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
    private static final LocalDate DE = LocalDate.of(2026, 5, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 5, 31);
    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    @BeforeEach void preparar() {
        usuario = usuarios.save(Usuario.builder().email("cmv-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        produto = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Laço de fita").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("15.00")).estoqueAtual(BigDecimal.ZERO).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(DE.atStartOfDay()).valorAbertura(BigDecimal.ZERO).status(StatusCaixaTurno.ABERTO).build());
    }
    @AfterEach void limparContexto() { SecurityContextHolder.clearContext(); }
    private Orcamento orcamento(LocalDate dia, StatusOrcamento status, String total, String custo, int numero) {
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numero).status(status)
                .dataEntrega(dia.atTime(23,59,59)).subtotal(bd(total)).total(bd(total)).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(produto).quantidade(1).precoUnitario(bd(total)).subtotal(bd(total))
                .custoMaterialUnitario(custo == null ? null : bd(custo)).build());
        return o;
    }
    private VendaCaixa caixa(LocalDate dia, StatusVendaCaixa status, String total, String qtd, String custo, int numero) {
        VendaCaixa v = vendas.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numero).status(status).caixaTurno(turno)
                .dataVenda(dia.atStartOfDay()).subtotal(bd(total)).total(bd(total)).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(produto).quantidade(bd(qtd)).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(custo == null ? null : bd(custo)).build());
        return v;
    }
    @Test void cen30_totalGlobalNaoSomentePagina_ePercentuaisDaSpec() {
        var cx = caixa(DE.plusDays(5), StatusVendaCaixa.CONCLUIDA, "45", "3", "6", 2);
        var orc = orcamento(DE.plusDays(19), StatusOrcamento.ENTREGUE, "100", "30", 14);
        var pagina = dashboard.vendasCmv(DE, ATE, null, PageRequest.of(0,1));
        assertEquals(bd("48.00"), pagina.totalCustoMaterial());
        assertEquals(2, pagina.vendas().getTotalElements());
        var o = pagina.vendas().getContent().getFirst();
        assertEquals(orc.getId(), o.id()); assertEquals("ORC-14", o.identificador()); assertEquals(TipoVendaCmv.ORCAMENTO, o.tipo());
        assertEquals("Ana", o.cliente()); assertEquals("1 × Laço de fita", o.itens()); assertEquals(bd("30.00"), o.cmvPercentual());
        var c = dashboard.vendasCmv(DE, ATE, null, PageRequest.of(1,1)).vendas().getContent().getFirst();
        assertEquals(cx.getId(), c.id()); assertEquals("CX-2", c.identificador()); assertEquals("3 × Laço de fita", c.itens());
        assertEquals(bd("40.00"), c.cmvPercentual()); assertFalse(c.estimado()); assertFalse(c.semCusto());
        assertEquals(pagina.totalCustoMaterial(), dashboard.dashboard(DE, ATE).cmv().valor());
    }
    @Test void cen57e58_mesUsaMesmoCmvDaBarra_eSerieVaziaZeroCen31() {
        caixa(DE.plusDays(5), StatusVendaCaixa.CONCLUIDA, "45", "3", "6", 2);
        orcamento(LocalDate.of(2026,6,20), StatusOrcamento.ENTREGUE, "100", "30", 14);
        LocalDate fim = LocalDate.of(2026,6,30);
        var d = dashboard.dashboard(DE, fim);
        var maio = dashboard.vendasCmv(DE, fim, YearMonth.of(2026,5), PageRequest.of(0,20));
        assertEquals(1, maio.vendas().getTotalElements()); assertEquals("CX-2", maio.vendas().getContent().getFirst().identificador());
        assertEquals(d.meses().stream().filter(m -> m.mes().equals(DE)).findFirst().orElseThrow().cmv(), maio.totalCustoMaterial());
        var junho = dashboard.vendasCmv(DE, fim, YearMonth.of(2026,6), PageRequest.of(0,20));
        assertEquals("ORC-14", junho.vendas().getContent().getFirst().identificador()); assertEquals(bd("30.00"), junho.totalCustoMaterial());
        assertEquals(bd("0.00"), d.meses().stream().filter(m -> m.mes().equals(LocalDate.of(2026,3,1))).findFirst().orElseThrow().cmvPercentual());
    }
    @Test void filtrosDatasStatusExclusaoEConta() {
        orcamento(DE, StatusOrcamento.ENTREGUE,"10","2",1);
        caixa(ATE,StatusVendaCaixa.CONCLUIDA,"10","1","2",2);
        orcamento(DE.minusDays(1),StatusOrcamento.ENTREGUE,"999","999",3);
        orcamento(ATE.plusDays(1),StatusOrcamento.ENTREGUE,"999","999",4);
        orcamento(DE,StatusOrcamento.RASCUNHO,"999","999",5);
        caixa(DE,StatusVendaCaixa.CANCELADA,"999","1","999",6);
        var excluido = orcamento(DE,StatusOrcamento.ENTREGUE,"999","999",7); excluido.setDeletedAt(LocalDateTime.now()); orcamentos.save(excluido);
        var outra = usuarios.save(Usuario.builder().email("outra-"+UUID.randomUUID()+"@test.com").senhaHash("x").ativo(true).build());
        var externo = orcamento(DE,StatusOrcamento.ENTREGUE,"999","999",8); externo.setUsuario(outra); orcamentos.save(externo);
        var p = dashboard.vendasCmv(DE,ATE,null,PageRequest.of(0,20));
        assertEquals(2,p.vendas().getTotalElements()); assertEquals(bd("4.00"),p.totalCustoMaterial());
    }
    @Test void naoRedondosEAArredondamentoCompartilhado() {
        caixa(DE,StatusVendaCaixa.CONCLUIDA,"43.19","3","5.8123",1);
        orcamento(DE,StatusOrcamento.ENTREGUE,"97.31","29.9951",2);
        var p = dashboard.vendasCmv(DE,ATE,null,PageRequest.of(0,20));
        assertEquals(bd("47.44"),p.totalCustoMaterial()); // 17,44 + 30,00
        assertEquals(p.totalCustoMaterial(), p.vendas().stream().map(v->v.custoMaterial()).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(p.totalCustoMaterial(),dashboard.dashboard(DE,ATE).cmv().valor());
        assertEquals(bd("40.38"),p.vendas().stream().filter(v->v.tipo()==TipoVendaCmv.VENDA_CAIXA).findFirst().orElseThrow().cmvPercentual());
    }
    @Test void estimadoSemCustoEFaturamentoZero() {
        var o = orcamento(DE,StatusOrcamento.ENTREGUE,"0",null,1);
        var p=dashboard.vendasCmv(DE,ATE,null,PageRequest.of(0,20));
        var linha=p.vendas().getContent().getFirst();
        assertEquals(o.getId(), linha.id()); assertTrue(linha.estimado()); assertFalse(linha.semCusto());
        assertEquals(bd("0.00"),linha.cmvPercentual());
    }
    @Test void paginaVaziaPreservaTotalEBloqueios() {
        caixa(DE,StatusVendaCaixa.CONCLUIDA,"45","3","6",2);
        var p=dashboard.vendasCmv(DE,ATE,null,PageRequest.of(3,20)); assertTrue(p.vendas().isEmpty()); assertEquals(bd("18.00"),p.totalCustoMaterial());
        assertThrows(BusinessException.class,()->dashboard.vendasCmv(ATE,DE,null,PageRequest.of(0,20)));
        assertThrows(BusinessException.class,()->dashboard.vendasCmv(DE,ATE,YearMonth.of(2027,1),PageRequest.of(0,20)));
        assertThrows(BusinessException.class,()->dashboard.vendasCmv(DE,ATE,null,PageRequest.of(0,101)));
    }
}
