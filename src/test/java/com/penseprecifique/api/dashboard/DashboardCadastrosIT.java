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

    /** #989 (RN-NOVA-29, DT-NOVA-24, CEN-NOVO-91): uma série por insumo, com a unidade, na ordem pedida e só com os pontos de cada um. */
    @Test
    void cen91_evolucaoComVariosInsumosDaUmaSeriePorInsumoComUnidade() {
        Insumo fita = insumo("Fita de cetim", "0.00");
        Insumo papel = insumo("Papel couchê", "0.00");
        Insumo cola = insumo("Cola quente", "0.00");
        compra(fita, "10", "20.00", LocalDate.of(2025, 9, 10));
        compra(fita, "10", "30.00", LocalDate.of(2025, 10, 10));
        compra(papel, "10", "3.00", LocalDate.of(2025, 9, 12));
        compra(papel, "10", "4.00", LocalDate.of(2025, 10, 12));
        compra(cola, "1", "5.00", LocalDate.of(2025, 10, 15));

        var r = service.evolucaoDosInsumos(List.of(cola.getId(), fita.getId(), papel.getId()), LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 31));

        assertEquals(List.of("Cola quente", "Fita de cetim", "Papel couchê"), r.series().stream().map(DashboardCadastrosResponse.EvolucaoPreco::insumo).toList());
        assertEquals("Metro", r.series().get(1).unidade());
        assertEquals(1, r.series().get(0).pontos().size()); // cola: só outubro
        assertEquals(0, bd("5.00").compareTo(r.series().get(0).pontos().get(0).precoMedio()));
        assertEquals(2, r.series().get(1).pontos().size());
        assertEquals(0, bd("2.00").compareTo(r.series().get(1).pontos().get(0).precoMedio()));
        assertEquals(0, bd("3.00").compareTo(r.series().get(1).pontos().get(1).precoMedio()));
    }

    /**
     * #989: o ponto é a média mensal PONDERADA da rotina de Cadastros, não um ponto por compra: no mesmo mês, 10 m por R$ 20,00 e 5 m
     * por R$ 20,00 dão 40 ÷ 15 = 2,6667, arredondado a R$ 2,67; a média simples dos dois preços por metro (2,00 e 4,00) seria R$ 3,00.
     */
    @Test
    void evolucaoDeVariosInsumosUsaAMediaMensalPonderadaESoUmPontoPorMes() {
        Insumo fita = insumo("Fita de cetim", "0.00");
        compra(fita, "10", "20.00", LocalDate.of(2025, 10, 5));
        compra(fita, "5", "20.00", LocalDate.of(2025, 10, 20));

        var serie = service.evolucaoDosInsumos(List.of(fita.getId()), LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31)).series().get(0);

        assertEquals(1, serie.pontos().size());
        assertEquals(0, bd("2.67").compareTo(serie.pontos().get(0).precoMedio()));
        assertEquals(0, bd("15").compareTo(serie.pontos().get(0).quantidade()));
    }

    /** #989 (CEN-NOVO-92): entradas inválidas são erro de cliente; repetido conta uma vez; insumo de outra conta não existe para quem pergunta. */
    @Test
    void cen92_evolucaoDeVariosInsumosRecusaVazioMaisDeCincoEInsumoDeOutraConta() {
        List<Insumo> seis = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) seis.add(insumo("Insumo " + i, "0.00"));
        List<UUID> idsSeis = seis.stream().map(Insumo::getId).toList();
        LocalDate de = LocalDate.of(2025, 10, 1), ate = LocalDate.of(2025, 10, 31);

        var vazio = org.junit.jupiter.api.Assertions.assertThrows(com.penseprecifique.api.shared.exception.BusinessException.class,
                () -> service.evolucaoDosInsumos(List.of(), de, ate));
        assertEquals("Escolha ao menos um insumo.", vazio.getMessage());
        var seisInsumos = org.junit.jupiter.api.Assertions.assertThrows(com.penseprecifique.api.shared.exception.BusinessException.class,
                () -> service.evolucaoDosInsumos(idsSeis, de, ate));
        assertEquals("Escolha no máximo 5 insumos", seisInsumos.getMessage());

        // cinco insumos podem; o mesmo insumo repetido seis vezes conta uma vez
        assertEquals(5, service.evolucaoDosInsumos(idsSeis.subList(0, 5), de, ate).series().size());
        assertEquals(1, service.evolucaoDosInsumos(java.util.Collections.nCopies(6, idsSeis.get(0)), de, ate).series().size());

        Usuario outro = usuarios.save(Usuario.builder().email("dash-cad-outro-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        UnidadeMedida unidadeOutro = unidades.save(UnidadeMedida.builder().usuario(outro).nome("Metro").sigla("m").build());
        Insumo alheio = insumos.save(Insumo.builder().usuario(outro).numero(1).nome("Alheio").unidadeMedida(unidadeOutro)
                .estoqueAtual(bd("1")).custoUnitario(bd("1")).permitirEstoqueNegativo(true).build());
        org.junit.jupiter.api.Assertions.assertThrows(com.penseprecifique.api.shared.exception.ResourceNotFoundException.class,
                () -> service.evolucaoDosInsumos(List.of(idsSeis.get(0), alheio.getId()), de, ate));
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

    /** V0.17.0 #1003 (QA da #971, CEN-NOVO-79): 12 insumos com 12, 11, ... 1 compras; o ranking mostra 10 e a lupa lista os 12. */
    @Test
    void cen79_rankingDoInsumoMaisCompradoMostraDezELupaListaTodos() {
        for (int n = 1; n <= 12; n++) {
            Insumo insumo = insumo("Insumo " + n, "1.00");
            for (int c = 0; c < 13 - n; c++) compra(insumo, "1", "1.00", DE.plusDays(c));
        }

        DashboardCadastrosResponse.Ranking r = service.dashboard(DE, ATE, null).insumoMaisComprado();

        assertEquals(10, r.top().size());
        assertEquals(12, r.todos().size());
        assertEquals("Insumo 1", r.top().get(0).rotulo());
        assertEquals("Insumo 10", r.top().get(9).rotulo());
        assertEquals(List.of("Insumo 11", "Insumo 12"), r.todos().subList(10, 12).stream().map(DashboardCadastrosResponse.Item::rotulo).toList(),
                "INS-11 (2 compras) e INS-12 (1 compra) só aparecem na lupa");
        assertEquals(0, bd("12").compareTo(r.todos().get(0).valor()));
        assertEquals(0, bd("1").compareTo(r.todos().get(11).valor()));
        assertTrue(r.top().stream().noneMatch(i -> i.rotulo().equals("Insumo 11") || i.rotulo().equals("Insumo 12")));
    }
}
