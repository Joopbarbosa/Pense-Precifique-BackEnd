package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 — #607 (RN-NOVA-16). Cenários CEN-NOVO-33, 34, 35, 36, 37, 52 e 53. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardCadastrosIT {

    @Autowired DashboardCadastrosService service;
    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired InsumoRepository insumos;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired FichaTecnicaItemRepository fichas;
    @Autowired VendaCaixaRepository vendas;
    @Autowired VendaCaixaItemRepository itensCaixa;
    @Autowired CaixaTurnoRepository turnos;

    private Usuario usuario;
    private Cliente cliente;
    private CaixaTurno turno;
    private UnidadeMedida metro;
    private int numeroProduto = 1;
    private int numeroInsumo = 1;
    private int numeroVenda = 1;
    private static final LocalDate DE = LocalDate.of(2026, 10, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 10, 31);

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("dash-cad-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(DE.atStartOfDay()).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.ABERTO).build());
        metro = unidades.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Produto produto(String nome, String precoVenda) {
        return produtos.save(Produto.builder().usuario(usuario).numero(numeroProduto++).nome(nome).tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(1).margemLucro(bd("50")).precoCusto(BigDecimal.ZERO)
                .precoVenda(bd(precoVenda)).estoqueAtual(BigDecimal.ZERO).build());
    }

    private Insumo insumo(String nome, String custo) {
        return insumo(nome, custo, null);
    }

    private Insumo insumo(String nome, String custo, String marca) {
        return insumos.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome).marca(marca).unidadeMedida(metro)
                .estoqueAtual(bd("100")).custoUnitario(bd(custo)).permitirEstoqueNegativo(true).build());
    }

    /** Venda do Caixa concluída de 1 unidade do produto: lucro = total − custo. */
    private void venda(Produto p, String total, String custo) {
        VendaCaixa v = vendas.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numeroVenda++).status(StatusVendaCaixa.CONCLUIDA)
                .caixaTurno(turno).dataVenda(DE.plusDays(5).atTime(10, 0)).subtotal(bd(total)).total(bd(total)).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(p).quantidade(BigDecimal.ONE).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(bd(custo)).build());
    }

    private void lucro(String nome, String lucro) {
        BigDecimal l = bd(lucro);
        Produto p = produto(nome, "10");
        // custo 10 fixo: total = lucro + 10 (lucro negativo: total menor que o custo)
        venda(p, l.add(bd("10")).toPlainString(), "10");
    }

    @Test
    void cen34_curvaAbcPorLucro() {
        lucro("Bolsa", "800"); lucro("Laço", "120"); lucro("Chaveiro", "50"); lucro("Adesivo", "30");

        var abc = service.dashboard(DE, ATE, null).curvaAbc();

        assertEquals(0, bd("1000").compareTo(abc.totalLucro()));
        assertEquals(List.of("Bolsa:A", "Laço:B", "Chaveiro:C", "Adesivo:C"),
                abc.itens().stream().map(i -> i.nome() + ":" + i.classe()).toList());
        assertEquals(0, bd("92.00").compareTo(abc.itens().get(1).acumulado()));
    }

    @Test
    void cen53_curvaAbcComEmpateLucroZeroEPrejuizo() {
        lucro("Bolsa", "600"); lucro("Chaveiro", "150"); lucro("Laço", "150"); lucro("Adesivo", "50"); lucro("Fita", "50");
        lucro("Capa", "0"); lucro("Caneca", "-100");

        var abc = service.dashboard(DE, ATE, null).curvaAbc();

        assertEquals(0, bd("1000").compareTo(abc.totalLucro()));
        assertEquals(List.of("Bolsa:A:60.00", "Chaveiro:A:75.00", "Laço:B:90.00", "Adesivo:B:95.00", "Fita:C:100.00"),
                abc.itens().stream().map(i -> i.nome() + ":" + i.classe() + ":" + i.acumulado().toPlainString()).toList());
        assertEquals(List.of("Caneca", "Capa"), abc.semLucro().stream().map(DashboardCadastrosResponse.ItemAbc::nome).sorted().toList());
    }

    @Test
    void cen53_semNenhumLucroPositivoAcurvaFicaVazia() {
        lucro("Capa", "0"); lucro("Caneca", "-100");

        var abc = service.dashboard(DE, ATE, null).curvaAbc();

        assertTrue(abc.itens().isEmpty());
        assertEquals(2, abc.semLucro().size());
    }

    @Test
    void cen33_precoDeVendaMenorQueOSugeridoNaoDependeDoPeriodo() {
        Insumo fita = insumo("Fita", "4.00");
        Insumo cola = insumo("Cola", "12.00");
        Produto laco = produto("Laço", "4.00");       // custo 4, margem 50% → sugerido 6
        Produto kit = produto("Kit Laços", "20.00");  // custo 12, margem 50% → sugerido 18
        fichas.save(FichaTecnicaItem.builder().produto(laco).insumo(fita).quantidade(BigDecimal.ONE).build());
        fichas.save(FichaTecnicaItem.builder().produto(kit).insumo(cola).quantidade(BigDecimal.ONE).build());

        var antes = service.dashboard(DE, ATE, null).produtosComPrecoAbaixoDoSugerido();
        var outroPeriodo = service.dashboard(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), null).produtosComPrecoAbaixoDoSugerido();

        assertEquals(1, antes.size());
        assertEquals("Laço", antes.get(0).nome());
        assertEquals(0, bd("-2.00").compareTo(antes.get(0).diferenca()));
        assertEquals(antes.get(0).id(), outroPeriodo.get(0).id());
    }

    @Test
    void cen36_margemPorProdutoComMargemNegativaPrimeiro() {
        Insumo e1 = insumo("Fita", "4.00");
        Insumo e2 = insumo("Couro", "25.00");
        Produto laco = produto("Laço", "10.00");
        Produto bolsa = produto("Bolsa", "20.00");
        fichas.save(FichaTecnicaItem.builder().produto(laco).insumo(e1).quantidade(BigDecimal.ONE).build());
        fichas.save(FichaTecnicaItem.builder().produto(bolsa).insumo(e2).quantidade(BigDecimal.ONE).build());

        var margens = service.dashboard(DE, ATE, null).margemPorProduto();

        assertEquals(List.of("Bolsa", "Laço"), margens.stream().map(DashboardCadastrosResponse.MargemProduto::nome).toList());
        assertEquals(0, bd("-25.00").compareTo(margens.get(0).margemPercentual()));
        assertEquals(0, bd("60.00").compareTo(margens.get(1).margemPercentual()));
    }

    @Test
    void cen37_semDadosNoPeriodoOsIndicadoresDePeriodoFicamVaziosMasAsListasDeCadastroContinuam() {
        Produto laco = produto("Laço", "4.00");
        Insumo fita = insumo("Fita", "4.00");
        fichas.save(FichaTecnicaItem.builder().produto(laco).insumo(fita).quantidade(BigDecimal.ONE).build());

        var d = service.dashboard(DE, ATE, null);

        assertTrue(d.insumoMaisComprado().todos().isEmpty());
        assertTrue(d.insumoMaisGasto().todos().isEmpty());
        assertTrue(d.produtoMaisVendido().todos().isEmpty());
        assertTrue(d.produtoMaisLucro().todos().isEmpty());
        assertTrue(d.curvaAbc().itens().isEmpty());
        assertTrue(d.insumosQueMaisVariaram().todos().isEmpty());
        assertEquals(1, d.margemPorProduto().size());
        assertEquals(1, d.produtosComPrecoAbaixoDoSugerido().size());
        assertEquals(1, d.insumosEmProdutos().todos().size());
    }

    @Test
    void cen35_insumosQueMaisVariaramSoComDuasComprasNoPeriodo() {
        Insumo fita = insumo("Fita de cetim", "0.00");
        Insumo cola = insumo("Cola", "0.00");
        LocalDate hoje = LocalDate.now();
        compra(fita, "10", "20.00", hoje.withDayOfMonth(1));
        compra(fita, "10", "30.00", hoje);
        compra(cola, "10", "50.00", hoje);

        var v = service.dashboard(hoje.withDayOfMonth(1), hoje, null).insumosQueMaisVariaram();

        assertEquals(1, v.todos().size());
        assertEquals("Fita de cetim", v.todos().get(0).rotulo());
        assertEquals(0, bd("50.00").compareTo(v.todos().get(0).valor()));
    }

    @Test
    void cen52_evolucaoDePrecoMedioPonderadoPorMesComMesSemCompraSemPonto() {
        Insumo fita = insumo("Fita de cetim", "0.00");
        compra(fita, "10", "20.00", LocalDate.of(2025, 10, 5));
        compra(fita, "30", "90.00", LocalDate.of(2025, 10, 20));
        compra(fita, "10", "40.00", LocalDate.of(2025, 11, 10));

        var evolucao = service.evolucaoDoInsumo(fita.getId(), LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        assertEquals(2, evolucao.pontos().size());
        assertEquals(LocalDate.of(2025, 10, 1), evolucao.pontos().get(0).mes());
        assertEquals(0, bd("2.75").compareTo(evolucao.pontos().get(0).precoMedio()));
        assertEquals(0, bd("4.00").compareTo(evolucao.pontos().get(1).precoMedio()));
    }

    @Test
    void rankingsDeInsumosComDesempateAlfabeticoEMarca() {
        Insumo alfa = insumo("Fita", "0.00", "Alfa");
        Insumo beta = insumo("Fita", "0.00", "Beta");
        LocalDate hoje = LocalDate.now();
        compra(beta, "1", "10.00", hoje);
        compra(alfa, "1", "10.00", hoje);

        var r = service.dashboard(hoje.withDayOfMonth(1), hoje, null).insumoMaisComprado();

        assertEquals(List.of("Alfa", "Beta"), r.todos().stream().map(DashboardCadastrosResponse.Item::marca).toList());
        assertNotNull(service.dashboard(hoje.withDayOfMonth(1), hoje, null).evolucaoPrecoInsumo());
    }

    private void compra(Insumo insumo, String qtd, String total, LocalDate dia) {
        compraService.confirmarNova(new CompraRequest(dia, false, null, false, null, null,
                List.of(new CompraItemRequest(insumo.getId(), null, bd(qtd), bd(total)))));
    }
}
