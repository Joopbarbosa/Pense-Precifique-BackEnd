package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.DashboardComprasResponse;
import com.penseprecifique.api.shared.dto.response.compra.EvolucaoPrecoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.15.0 — #548 (RN-NOVA-15) e #577 (RN-NOVA-29). Cenários CEN-NOVO-24, 25, 40 e 41. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardCompraIT {

    @Autowired DashboardCompraService dashboardCompraService;
    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ClienteRepository clienteRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numeroInsumo = 1;
    private int numeroCadastro = 1;
    private final LocalDate hoje = LocalDate.now();

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("dash-compra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private Cliente fornecedor(String nome) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCadastro++).nome(nome)
                .ehCliente(false).ehFornecedor(true).ativa(true).build());
    }

    private CompraResponse confirmada(LocalDate data, Cliente f, Insumo i, String qtd, String preco) {
        return compraService.confirmarNova(new CompraRequest(data, false, f != null ? f.getId() : null, false, null, null,
                List.of(new CompraItemRequest(i.getId(), null, new BigDecimal(qtd), new BigDecimal(preco))))).compra();
    }

    // Período fixo (setembro/2026) para não depender do dia em que a suíte roda.
    private static final LocalDate SET_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SET_30 = LocalDate.of(2026, 9, 30);

    private DashboardComprasResponse setembro() {
        return dashboardCompraService.dashboard(SET_1, SET_30);
    }

    @Test
    void cen24_dashboardIgnoraRascunhoECancelada() {
        Insumo cola = insumo("Cola");
        confirmada(SET_1.plusDays(4), null, cola, "10", "100.00");
        CompraResponse cancelada = confirmada(SET_1.plusDays(5), null, cola, "5", "50.00");
        compraService.cancelar(cancelada.id(), new CancelarCompraRequest("Pedido duplicado por engano no sistema.", true));
        compraService.criarRascunho(new CompraRequest(SET_1.plusDays(6), false, null, false, null, null,
                List.of(new CompraItemRequest(cola.getId(), null, BigDecimal.ONE, new BigDecimal("30.00")))));

        DashboardComprasResponse d = setembro();
        assertEquals(0, new BigDecimal("100.00").compareTo(d.gasto().valor()));
        assertEquals(0, BigDecimal.ONE.compareTo(d.quantidadeCompras().valor()));
    }

    @Test
    void cen40_numerosComparamComOPeriodoAnterior() {
        // Mês "atual" começando no dia 1 → anterior = agosto inteiro.
        Insumo cola = insumo("Cola");
        Cliente armarinho = fornecedor("Armarinho Boa Linha");
        confirmada(LocalDate.of(2026, 9, 14), armarinho, cola, "2", "158.10");
        confirmada(LocalDate.of(2026, 8, 2), armarinho, cola, "1", "128.65");
        confirmada(LocalDate.of(2026, 7, 31), armarinho, cola, "1", "999.00"); // fora dos dois períodos

        DashboardComprasResponse d = setembro();
        assertEquals(LocalDate.of(2026, 8, 1), d.deAnterior());
        assertEquals(LocalDate.of(2026, 8, 31), d.ateAnterior());
        assertEquals(0, new BigDecimal("158.10").compareTo(d.gasto().valor()));
        assertEquals(0, new BigDecimal("128.65").compareTo(d.gasto().anterior()));
        assertEquals(new BigDecimal("22.89"), d.gasto().variacaoPercentual()); // (158,10 − 128,65) ÷ 128,65
        assertEquals(0, new BigDecimal("158.10").compareTo(d.ticketMedio().valor()));
        // Série mensal: no mínimo 6 meses terminando em setembro (abr–set).
        assertEquals(6, d.meses().size());
        assertEquals(LocalDate.of(2026, 4, 1), d.meses().get(0).mes());
        assertEquals(0, new BigDecimal("999.00").compareTo(d.meses().get(3).gasto())); // julho
        assertEquals("Armarinho Boa Linha", d.fornecedoresPorGasto().get(0).fornecedor().nome());
        assertEquals(1, d.fornecedoresPorGasto().get(0).quantidadeCompras());
    }

    @Test
    void cen41_fornecedoresQueMaisDeramDesconto() {
        Insumo fita = insumo("Fita de cetim");
        Insumo cola = insumo("Cola Branca 1L");
        Insumo papel = insumo("Papel");
        Cliente armarinho = fornecedor("Armarinho");
        Cliente papelaria = fornecedor("Papelaria");
        compraService.confirmarNova(new CompraRequest(SET_1.plusDays(9), false, armarinho.getId(), false, null, null, List.of(
                new CompraItemRequest(fita.getId(), null, new BigDecimal("10"), null, new BigDecimal("30.00"), TipoDesconto.PERCENTUAL, new BigDecimal("10")),
                new CompraItemRequest(cola.getId(), null, new BigDecimal("3"), null, new BigDecimal("45.90"), TipoDesconto.VALOR, new BigDecimal("1.90"))),
                TipoDesconto.PERCENTUAL, new BigDecimal("5")));
        compraService.confirmarNova(new CompraRequest(SET_1.plusDays(10), false, papelaria.getId(), false, null, null, List.of(
                new CompraItemRequest(papel.getId(), null, new BigDecimal("100"), null, new BigDecimal("60.00"), TipoDesconto.VALOR, new BigDecimal("2.00"))),
                null, null));

        DashboardComprasResponse d = setembro();
        assertEquals(0, new BigDecimal("10.45").compareTo(d.economia().valor()));      // 8,45 + 2,00
        assertEquals(0, new BigDecimal("135.90").compareTo(d.economia().totalCheio())); // 75,90 + 60,00
        assertEquals(new BigDecimal("7.69"), d.economia().percentual());
        List<DashboardComprasResponse.FornecedorDesconto> pct = d.fornecedoresPorDescontoPercentual();
        assertEquals("Armarinho", pct.get(0).fornecedor().nome());
        assertEquals(new BigDecimal("11.13"), pct.get(0).percentual());
        assertEquals(new BigDecimal("3.33"), pct.get(1).percentual());
        assertEquals("Armarinho", d.fornecedoresPorDescontoValor().get(0).fornecedor().nome());
    }

    @Test
    void insumosQueMaisSubiramNoPeriodo() {
        Insumo cola = insumo("Cola");
        Insumo fita = insumo("Fita");
        Insumo papel = insumo("Papel");
        confirmada(SET_1.plusDays(1), null, cola, "2", "24.00");  // 12,00
        confirmada(SET_1.plusDays(20), null, cola, "2", "30.00"); // 15,00 → +25,00%
        confirmada(SET_1.plusDays(2), null, fita, "10", "15.00"); // 1,50
        confirmada(SET_1.plusDays(21), null, fita, "10", "16.50"); // 1,65 → +10,00%
        confirmada(SET_1.plusDays(3), null, papel, "1", "9.00");
        confirmada(SET_1.plusDays(22), null, papel, "1", "8.00");  // caiu: não entra

        DashboardComprasResponse d = setembro();
        assertEquals(List.of("Cola", "Fita"), d.insumosQueMaisSubiram().stream().map(x -> x.insumo().nome()).toList());
        assertEquals(new BigDecimal("25.00"), d.maiorAumento().variacaoPercentual());
    }

    @Test
    void semDadosSuficientes() {
        Insumo cola = insumo("Cola");
        confirmada(SET_1.plusDays(1), null, cola, "1", "12.00");
        DashboardComprasResponse d = setembro();
        assertNull(d.maiorAumento());
        assertEquals(List.of(), d.fornecedoresPorGasto());
        assertNull(d.cmv().percentual()); // sem vendas, sem faturamento
    }

    @Test
    void listagemFiltraPorInsumoEBusca() {
        Insumo cola = insumo("Cola Branca");
        Insumo fita = insumo("Fita de cetim");
        Cliente papelaria = fornecedor("Papelaria Central");
        CompraResponse c1 = confirmada(SET_1.plusDays(1), papelaria, cola, "1", "12.40");
        CompraResponse c2 = confirmada(SET_1.plusDays(2), null, fita, "1", "7.35");
        org.springframework.data.domain.PageRequest p = org.springframework.data.domain.PageRequest.of(0, 20);
        assertEquals(List.of(c1.identificador()), compraService.listar(null, null, null, null, cola.getId(), null, p)
                .map(r -> r.identificador()).getContent());
        assertEquals(List.of(c2.identificador()), compraService.listar(null, null, null, null, null, "cetim", p)
                .map(r -> r.identificador()).getContent());
        assertEquals(List.of(c1.identificador()), compraService.listar(null, null, null, null, null, "papelaria", p)
                .map(r -> r.identificador()).getContent());
        assertEquals(List.of(c2.identificador()), compraService.listar(null, null, null, null, null, c2.identificador(), p)
                .map(r -> r.identificador()).getContent());
        assertEquals("Cola Branca ×1 un", compraService.listar(null, null, null, null, cola.getId(), null, p).getContent().get(0).resumoItens());
    }

    @Test
    void cen25_graficoDePrecoPago() {
        Insumo cola = insumo("Cola Branca 1L");
        Cliente papelaria = fornecedor("Papelaria Central");
        confirmada(hoje.minusDays(50), papelaria, cola, "3", "36.00"); // 12,00
        confirmada(hoje.minusDays(10), null, cola, "2", "30.00");      // 15,00
        confirmada(hoje.minusMonths(5), null, cola, "1", "1.00");      // fora de 3 meses

        EvolucaoPrecoResponse e = dashboardCompraService.evolucaoPreco(List.of(cola.getId()), null, null);
        assertEquals(hoje.minusMonths(3), e.de());
        List<EvolucaoPrecoResponse.Ponto> pontos = e.series().get(0).pontos();
        assertEquals(2, pontos.size());
        assertEquals(0, new BigDecimal("12.00").compareTo(pontos.get(0).precoUnitarioPago()));
        assertEquals("Papelaria Central", pontos.get(0).fornecedor());
        assertEquals(hoje.minusDays(50), pontos.get(0).data());
        assertEquals(new BigDecimal("0.00"), pontos.get(0).variacaoPercentual());
        assertEquals(0, new BigDecimal("15.00").compareTo(pontos.get(1).precoUnitarioPago()));
        assertEquals(new BigDecimal("25.00"), pontos.get(1).variacaoPercentual());
        assertEquals("COM-2", pontos.get(1).identificador());
    }

    @Test
    void graficoLimitaA5Insumos() {
        List<UUID> seis = List.of(insumo("A").getId(), insumo("B").getId(), insumo("C").getId(),
                insumo("D").getId(), insumo("E").getId(), insumo("F").getId());
        assertEquals("Escolha no máximo 5 insumos.", assertThrows(BusinessException.class,
                () -> dashboardCompraService.evolucaoPreco(seis, null, null)).getMessage());
        assertEquals("Escolha pelo menos um insumo.", assertThrows(BusinessException.class,
                () -> dashboardCompraService.evolucaoPreco(List.of(), null, null)).getMessage());
    }

    private static final String MSG_FAIXA = "As datas precisam estar entre 01/01/2000 e 31/12/2100.";

    /** #769 — datas extremas devolvem "Período inválido" (400) em vez de estourar o timestamp do banco (500). */
    @Test
    void periodoForaDeDoisMilAteDoisMilECemERejeitado() {
        UUID insumoId = insumo("A").getId();
        assertEquals(MSG_FAIXA, assertThrows(BusinessException.class,
                () -> dashboardCompraService.dashboard(LocalDate.of(845, 1, 16), LocalDate.of(9523, 8, 8))).getMessage());
        assertEquals(MSG_FAIXA, assertThrows(BusinessException.class,
                () -> dashboardCompraService.dashboard(LocalDate.of(1999, 12, 31), SET_30)).getMessage());
        assertEquals(MSG_FAIXA, assertThrows(BusinessException.class,
                () -> dashboardCompraService.evolucaoPreco(List.of(insumoId), SET_1, LocalDate.of(2101, 1, 1))).getMessage());
        assertNotNull(dashboardCompraService.dashboard(LocalDate.of(2000, 1, 1), SET_30));
    }
}
