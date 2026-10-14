package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraConfirmacaoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.DashboardComprasResponse;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 — #959 (RN-NOVA-7, RN-NOVA-8, DT-NOVA-2, 3 e 4). Cenários CEN-NOVO-16, 17, 18, 19, 22 e 23. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraEntradaIT {

    @Autowired CompraService compraService;
    @Autowired DashboardCompraService dashboardCompraService;
    @Autowired InsumoService insumoService;
    @Autowired CompraRepository compraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;

    private Usuario usuario;
    private UnidadeMedida metro;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("entrada-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        metro = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
    }

    private Insumo insumo(String nome, String estoque, String custo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero((int) (Math.random() * 100000)).nome(nome)
                .unidadeMedida(metro).estoqueAtual(new BigDecimal(estoque)).custoUnitario(new BigDecimal(custo))
                .permitirEstoqueNegativo(true).build());
    }

    private CompraResponse pendente(Insumo insumo, String quantidade, String preco, LocalDate dataCompra) {
        List<CompraItemRequest> itens = List.of(new CompraItemRequest(insumo.getId(), null, new BigDecimal(quantidade), new BigDecimal(preco)));
        return compraService.confirmarNova(new CompraRequest(dataCompra, false, null, false, null, null, itens, null, null, null,
                false, LocalDate.now().plusDays(7))).compra();
    }

    private EntradaCompraRequest entrada(CompraResponse compra, LocalDate data, String recebida) {
        return new EntradaCompraRequest(data, compra.itens().stream()
                .map(i -> new EntradaCompraRequest.ItemEntrada(i.id(), recebida == null ? i.quantidade() : new BigDecimal(recebida))).toList());
    }

    private Insumo atual(Insumo i) {
        return insumoRepository.findById(i.getId()).orElseThrow();
    }

    @Test
    void cen16_entradaAtualizaEstoqueCustoMovimentacaoEImpacto() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        CompraResponse c = pendente(fita, "10", "30.00", LocalDate.now());

        CompraConfirmacaoResponse r = compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), null));

        assertEquals(StatusCompra.CONFIRMADA, r.compra().status());
        assertEquals(0, new BigDecimal("20").compareTo(atual(fita).getEstoqueAtual()));
        assertEquals(0, new BigDecimal("2.50").compareTo(atual(fita).getCustoUnitario()));
        assertEquals(LocalDate.now(), r.compra().dataEntrada());
        assertTrue(r.impacto().alterouCustos());
        List<MovimentacaoInsumo> movs = movimentacaoInsumoRepository.findAll().stream()
                .filter(m -> m.getInsumo().getId().equals(fita.getId())).toList();
        assertEquals(1, movs.size());
        assertEquals(0, new BigDecimal("10").compareTo(movs.get(0).getQuantidade()));
        assertEquals(c.id(), movs.get(0).getReferenciaId());
    }

    @Test
    void cen17_quantidadeRecebidaDiferenteMantemOPrecoUnitarioPago() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        CompraResponse c = pendente(fita, "10", "30.00", LocalDate.now());

        CompraResponse r = compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), "8")).compra();

        assertEquals(0, new BigDecimal("18").compareTo(atual(fita).getEstoqueAtual()));
        assertEquals(0, new BigDecimal("2.4444").compareTo(atual(fita).getCustoUnitario()));
        assertEquals(0, new BigDecimal("24.00").compareTo(r.total()));
        assertEquals(0, new BigDecimal("3.0000").compareTo(r.itens().get(0).precoUnitarioPago()));
        assertEquals(0, new BigDecimal("3.0000").compareTo(r.itens().get(0).precoUnitario()));
        assertEquals(0, new BigDecimal("10").compareTo(r.itens().get(0).quantidadePedida()));
        assertEquals(StatusCompra.CONFIRMADA, r.status());
    }

    @Test
    void dt2_divergenciaComPrecoNaoRedondoUsaQuantidadeRecebidaVezesPrecoGravado() {
        Insumo fita = insumo("Fita", "0", "0.00");
        CompraResponse c = pendente(fita, "3", "10.00", LocalDate.now());

        CompraResponse r = compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), "2")).compra();

        assertEquals(0, new BigDecimal("3.3333").compareTo(r.itens().get(0).precoUnitarioPago()));
        assertEquals(0, new BigDecimal("6.67").compareTo(r.total()));
        assertEquals(0, new BigDecimal("3.3333").compareTo(atual(fita).getCustoUnitario()));
    }

    @Test
    void dt2_semDivergenciaOCustoMedioContinuaIdenticoAoDeHoje() {
        // estoque de 1 un a R$ 2,0002 e 3 un por R$ 10,00: o total original entra no cálculo → R$ 3,0001
        Insumo fita = insumo("Fita", "1", "2.0002");
        CompraResponse c = pendente(fita, "3", "10.00", LocalDate.now());

        compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), null));

        assertEquals(0, new BigDecimal("3.0001").compareTo(atual(fita).getCustoUnitario()));
    }

    @Test
    void dt2_descontosDaLinhaEDaNotaSaoEscaladosEOPrecoUnitarioPagoNaoMuda() {
        Insumo fita = insumo("Fita", "0", "0.00");
        List<CompraItemRequest> itens = List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal("10"), null, new BigDecimal("30.00"),
                com.penseprecifique.api.shared.domain.enums.TipoDesconto.VALOR, new BigDecimal("3.00")));
        CompraResponse c = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null, itens,
                com.penseprecifique.api.shared.domain.enums.TipoDesconto.VALOR, new BigDecimal("2.70"), null,
                false, LocalDate.now().plusDays(3))).compra();
        // cheio 30,00 − linha 3,00 − nota 2,70 = 24,30 pagos → 2,43 por metro

        CompraResponse r = compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), "5")).compra();

        assertEquals(0, new BigDecimal("2.4300").compareTo(r.itens().get(0).precoUnitarioPago()));
        assertEquals(0, new BigDecimal("12.15").compareTo(r.total()));
        assertEquals(0, new BigDecimal("15.00").compareTo(r.itens().get(0).precoCheio()));
        assertEquals(0, new BigDecimal("1.50").compareTo(r.itens().get(0).descontoLinha()));
        assertEquals(0, new BigDecimal("1.35").compareTo(r.itens().get(0).descontoNota()));
        assertEquals(0, new BigDecimal("1.35").compareTo(r.descontoNota()));
    }

    @Test
    void cen18_dataFuturaAnteriorACompraOuQuantidadeZeroBloqueiam() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        LocalDate dataCompra = LocalDate.now().minusDays(6);
        CompraResponse c = pendente(fita, "10", "30.00", dataCompra);

        BusinessException futura = assertThrows(BusinessException.class,
                () -> compraService.darEntrada(c.id(), entrada(c, LocalDate.now().plusDays(1), null)));
        assertTrue(futura.getMessage().contains("não pode ser futura"));
        BusinessException anterior = assertThrows(BusinessException.class,
                () -> compraService.darEntrada(c.id(), entrada(c, dataCompra.minusDays(1), null)));
        assertTrue(anterior.getMessage().contains("anterior à data da compra"));
        BusinessException zero = assertThrows(BusinessException.class,
                () -> compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), "0")));
        assertTrue(zero.getItens().get(0).contains("informe uma quantidade maior que zero; para tirar o item, edite a compra pendente"));

        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, compraService.buscar(c.id()).status());
        assertEquals(0, new BigDecimal("10").compareTo(atual(fita).getEstoqueAtual()));
    }

    @Test
    void cen19_dataDeRecebimentoNoPassadoApareceNaMovimentacaoEContaNoPeriodo() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        LocalDate compra = LocalDate.now().minusDays(6);
        LocalDate recebimento = LocalDate.now().minusDays(2);
        CompraResponse c = pendente(fita, "10", "30.00", compra);

        CompraResponse r = compraService.darEntrada(c.id(), entrada(c, recebimento, null)).compra();

        assertEquals(recebimento, r.dataEntrada());
        MovimentacaoInsumo mov = movimentacaoInsumoRepository.findAll().stream()
                .filter(m -> m.getInsumo().getId().equals(fita.getId())).findFirst().orElseThrow();
        assertEquals(recebimento, mov.getDataReferencia());
        assertNotNull(mov.getCreatedAt());
        // a compra conta no período do recebimento, não no da compra
        DashboardComprasResponse dash = dashboardCompraService.dashboard(recebimento, recebimento);
        assertEquals(0, new BigDecimal("30.00").compareTo(dash.gasto().valor()));
        DashboardComprasResponse dashDaCompra = dashboardCompraService.dashboard(compra, compra);
        assertEquals(0, BigDecimal.ZERO.compareTo(dashDaCompra.gasto().valor()));
    }

    @Test
    void cen22_insumoInativadoAntesDaEntradaBloqueiaATudo() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        CompraResponse c = pendente(fita, "10", "30.00", LocalDate.now());
        insumoService.inativar(fita.getId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), null)));

        assertTrue(e.getItens().contains("Fita de cetim: insumo inativo"), e.getItens().toString());
        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, compraService.buscar(c.id()).status());
        assertEquals(0, new BigDecimal("10").compareTo(atual(fita).getEstoqueAtual()));
    }

    @Test
    void cen23_pendenteFicaForaDoDashboardAteAEntrada() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        CompraResponse c = pendente(fita, "10", "30.00", LocalDate.now());
        LocalDate hoje = LocalDate.now();

        assertEquals(0, BigDecimal.ZERO.compareTo(dashboardCompraService.dashboard(hoje, hoje).gasto().valor()));
        compraService.darEntrada(c.id(), entrada(c, hoje, null));
        assertEquals(0, new BigDecimal("30.00").compareTo(dashboardCompraService.dashboard(hoje, hoje).gasto().valor()));
    }

    @Test
    void entradaSoEmCompraPendenteECompraEntradaDuasVezesBloqueia() {
        Insumo fita = insumo("Fita de cetim", "10", "2.00");
        CompraResponse c = pendente(fita, "10", "30.00", LocalDate.now());
        compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), null));

        assertThrows(BusinessException.class, () -> compraService.darEntrada(c.id(), entrada(c, LocalDate.now(), null)));
        assertEquals(0, new BigDecimal("20").compareTo(atual(fita).getEstoqueAtual()));
    }
}
