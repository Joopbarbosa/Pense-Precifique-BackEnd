package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.financeiro.Lancamento;
import com.penseprecifique.api.financeiro.LancamentoFinanceiroService;
import com.penseprecifique.api.financeiro.LancamentoRepository;
import com.penseprecifique.api.financeiro.OrigemBaixa;
import com.penseprecifique.api.financeiro.Parcela;
import com.penseprecifique.api.financeiro.ParcelaRepository;
import com.penseprecifique.api.financeiro.SituacaoLancamento;
import com.penseprecifique.api.financeiro.SituacaoParcela;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.19.0 (#1173, F2, DT-NOVA-3, RN-NOVA-6 a 9) — a compra gera, substitui e cancela parcelas do financeiro na própria
 * transação, e Pago/Não pago é derivado das parcelas. CEN-NOVO-1 a 7, 58, 60, 65, 66 e 68.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraFinanceiroIT {

    @Autowired CompraService compraService;
    @Autowired CompraRepository compraRepository;
    @Autowired LancamentoFinanceiroService financeiro;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoRepository;
    @Autowired Clock relogioBrasilia;

    private Usuario usuario;
    private Insumo fita;
    private MetodoPagamentoConfiguravel pix;
    private MetodoPagamentoConfiguravel dinheiro;
    private MetodoPagamentoConfiguravel credito;
    private LocalDate hoje;

    @BeforeEach
    void seed() {
        hoje = LocalDate.now(relogioBrasilia);
        usuario = usuarioRepository.save(Usuario.builder()
                .email("compra-fin-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        UnidadeMedida m = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
        fita = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Fita de cetim")
                .unidadeMedida(m).estoqueAtual(new BigDecimal("10")).custoUnitario(new BigDecimal("2.00"))
                .permitirEstoqueNegativo(true).build());
        pix = metodo(TipoMetodoPagamento.PIX, "Pix", null);
        dinheiro = metodo(TipoMetodoPagamento.DINHEIRO, "Dinheiro", null);
        credito = metodo(TipoMetodoPagamento.CARTAO_CREDITO, "Cartão", 24);
    }

    private MetodoPagamentoConfiguravel metodo(TipoMetodoPagamento tipo, String nome, Integer maxParcelas) {
        return metodoRepository.save(MetodoPagamentoConfiguravel.builder().usuario(usuario).tipo(tipo).nome(nome + " " + UUID.randomUUID())
                .ativo(true).ordem(1).maxParcelas(maxParcelas).build());
    }

    private CompraRequest pedido(String total, boolean pago, MetodoPagamentoConfiguravel metodo, Integer parcelas, LocalDate vencimento) {
        return pedido(total, pago, metodo, parcelas, vencimento, hoje);
    }

    private CompraRequest pedido(String total, boolean pago, MetodoPagamentoConfiguravel metodo, Integer parcelas,
                                 LocalDate vencimento, LocalDate dataCompra) {
        List<CompraItemRequest> itens = List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, new BigDecimal(total)));
        return new CompraRequest(dataCompra, false, null, pago, metodo == null ? null : metodo.getId(), null, itens,
                null, null, parcelas, true, null, vencimento);
    }

    private CompraResponse confirmar(CompraRequest r) {
        return compraService.confirmarNova(r).compra();
    }

    private Lancamento lancamentoDe(UUID compraId) {
        return lancamentoRepository.findAll().stream()
                .filter(l -> l.getCompra() != null && l.getCompra().getId().equals(compraId)).findFirst().orElse(null);
    }

    private List<Parcela> parcelas(UUID compraId) {
        return parcelaRepository.findByLancamentoIdOrderByNumeroAsc(lancamentoDe(compraId).getId());
    }

    private List<Parcela> ativas(UUID compraId) {
        return parcelas(compraId).stream().filter(p -> p.getSituacao() != SituacaoParcela.CANCELADA).toList();
    }

    private Compra compraBanco(UUID id) {
        return compraRepository.findById(id).orElseThrow();
    }

    /** CEN-NOVO-1 */
    @Test
    void cen1_compraNaoPagaGeraUmaParcelaEmAberto() {
        LocalDate venc = hoje.plusDays(10);
        CompraResponse c = confirmar(pedido("300.00", false, null, null, venc));
        Lancamento l = lancamentoDe(c.id());
        assertNotNull(l);
        assertEquals(new BigDecimal("300.00"), l.getValorTotal());
        List<Parcela> ps = parcelas(c.id());
        assertEquals(1, ps.size());
        assertEquals(SituacaoParcela.EM_ABERTO, ps.get(0).getSituacao());
        assertEquals(new BigDecimal("300.00"), ps.get(0).getValor());
        assertEquals(venc, ps.get(0).getVencimento());
        assertEquals(Boolean.FALSE, c.pago());
    }

    /** CEN-NOVO-2 */
    @Test
    void cen2_compraNaoPagaSemVencimentoEBloqueada() {
        long antes = compraRepository.count();
        BusinessException e = assertThrows(BusinessException.class,
                () -> confirmar(pedido("300.00", false, null, null, null)));
        assertEquals("Informe quando esta compra vence.", e.getMessage());
        assertEquals(antes, compraRepository.count());
        assertTrue(lancamentoRepository.findAll().stream().noneMatch(l -> l.getUsuario().getId().equals(usuario.getId())));
    }

    /** CEN-NOVO-3 e DT-NOVA-3: cartão em 3x grava com método e "Não paga", sem violar o banco. */
    @Test
    void cen3_creditoEm3xDividePorCentavosEFicaNaoPagoComMetodo() {
        LocalDate primeiro = hoje.plusDays(30);
        CompraResponse c = confirmar(pedido("100.00", true, credito, 3, primeiro));
        List<Parcela> ps = parcelas(c.id());
        assertEquals(3, ps.size());
        assertEquals(new BigDecimal("33.33"), ps.get(0).getValor());
        assertEquals(new BigDecimal("33.33"), ps.get(1).getValor());
        assertEquals(new BigDecimal("33.34"), ps.get(2).getValor());
        assertEquals(primeiro, ps.get(0).getVencimento());
        assertEquals(primeiro.plusMonths(1), ps.get(1).getVencimento());
        assertEquals(primeiro.plusMonths(2), ps.get(2).getVencimento());
        assertTrue(ps.stream().allMatch(p -> p.getSituacao() == SituacaoParcela.EM_ABERTO));
        assertEquals(Boolean.FALSE, c.pago());
        Compra banco = compraBanco(c.id());
        assertFalse(banco.getPago());
        assertEquals(credito.getId(), banco.getMetodoPagamento().getId());
        assertEquals(3, banco.getParcelas());
    }

    @Test
    void creditoSemVencimentoUsaUmMesAposADataDaCompra() {
        CompraResponse c = confirmar(pedido("90.00", true, credito, 2, null));
        assertEquals(hoje.plusMonths(1), parcelas(c.id()).get(0).getVencimento());
    }

    /** CEN-NOVO-4 */
    @Test
    void cen4_compraPagaEmPixNasceBaixadaNaDataDaCompra() {
        CompraResponse c = confirmar(pedido("80.00", true, pix, null, null));
        List<Parcela> ps = parcelas(c.id());
        assertEquals(1, ps.size());
        Parcela p = ps.get(0);
        assertEquals(SituacaoParcela.BAIXADA, p.getSituacao());
        assertEquals(OrigemBaixa.AUTOMATICA, p.getOrigemBaixa());
        assertEquals(hoje, p.getDataBaixa());
        assertEquals(pix.getId(), p.getMetodoPagamento().getId());
        assertEquals(Boolean.TRUE, c.pago());
    }

    /** CEN-NOVO-5 */
    @Test
    void cen5_cancelarComParcelaBaixadaPelaUsuariaEBloqueado() {
        CompraResponse c = confirmar(pedido("100.00", true, credito, 3, hoje.plusDays(5)));
        financeiro.baixar(parcelas(c.id()).get(0).getId(), hoje, pix.getId());
        BusinessException e = assertThrows(BusinessException.class,
                () -> compraService.cancelar(c.id(), new CancelarCompraRequest(null, null)));
        assertEquals("Desfaça os pagamentos já marcados antes de cancelar a compra.", e.getMessage());
        assertEquals(StatusCompra.CONFIRMADA, compraBanco(c.id()).getStatus());
        List<Parcela> ps = parcelas(c.id());
        assertEquals(SituacaoParcela.BAIXADA, ps.get(0).getSituacao());
        assertEquals(SituacaoParcela.EM_ABERTO, ps.get(1).getSituacao());
        assertEquals(SituacaoParcela.EM_ABERTO, ps.get(2).getSituacao());
    }

    /** CEN-NOVO-6 */
    @Test
    void cen6_cancelarSoComEmAbertoCancelaParcelasEPagoViraNulo() {
        CompraResponse c = confirmar(pedido("300.00", false, null, null, hoje.plusDays(10)));
        CompraResponse cancelada = compraService.cancelar(c.id(), new CancelarCompraRequest(null, null)).compra();
        assertEquals(StatusCompra.CANCELADA, cancelada.status());
        assertNull(cancelada.pago());
        Lancamento l = lancamentoDe(c.id());
        assertEquals(SituacaoLancamento.CANCELADO, l.getSituacao());
        assertEquals(0, l.getValorTotal().compareTo(BigDecimal.ZERO));
        assertEquals(SituacaoParcela.CANCELADA, parcelas(c.id()).get(0).getSituacao());
        assertNull(compraService.buscar(c.id()).pago());
    }

    /** CEN-NOVO-7 e RN-NOVA-9 */
    @Test
    void cen7_baixarUltimaParcelaMarcaCompraComoPagaEDesfazerReabre() {
        CompraResponse c = confirmar(pedido("100.00", true, credito, 3, hoje.plusDays(5)));
        List<Parcela> ps = parcelas(c.id());
        financeiro.baixar(ps.get(0).getId(), hoje, pix.getId());
        financeiro.baixar(ps.get(1).getId(), hoje, pix.getId());
        assertFalse(compraBanco(c.id()).getPago());
        financeiro.baixar(ps.get(2).getId(), hoje, pix.getId());
        assertTrue(compraBanco(c.id()).getPago());
        assertEquals(Boolean.TRUE, compraService.buscar(c.id()).pago());
        financeiro.desfazerBaixa(ps.get(1).getId());
        assertFalse(compraBanco(c.id()).getPago());
    }

    @Test
    void naoPagaSoPassaAPagaComMetodoDaBaixaEVoltaSemMetodoAoDesfazer() {
        CompraResponse c = confirmar(pedido("50.00", false, null, null, hoje.plusDays(2)));
        Parcela p = parcelas(c.id()).get(0);
        financeiro.baixar(p.getId(), hoje, pix.getId());
        Compra pago = compraBanco(c.id());
        assertTrue(pago.getPago());
        assertEquals(pix.getId(), pago.getMetodoPagamento().getId());
        financeiro.desfazerBaixa(p.getId());
        Compra reaberta = compraBanco(c.id());
        assertFalse(reaberta.getPago());
        assertNull(reaberta.getMetodoPagamento());
    }

    /** CEN-NOVO-58 */
    @Test
    void cen58_valorPequenoDemaisParaDividir() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> confirmar(pedido("0.01", true, credito, 2, hoje.plusDays(5))));
        assertEquals("O valor é pequeno demais para dividir em 2 parcelas.", e.getMessage());
        CompraResponse c = confirmar(pedido("0.01", true, credito, 1, hoje.plusDays(5)));
        assertEquals(new BigDecimal("0.01"), parcelas(c.id()).get(0).getValor());
    }

    /** CEN-NOVO-60 */
    @Test
    void cen60_compraPagaAVistaCanceladaCancelaAParcelaSemEstorno() {
        CompraResponse c = confirmar(pedido("100.00", true, pix, null, null));
        compraService.cancelar(c.id(), new CancelarCompraRequest(null, null));
        Lancamento l = lancamentoDe(c.id());
        assertEquals(SituacaoLancamento.CANCELADO, l.getSituacao());
        assertEquals(0, l.getValorTotal().compareTo(BigDecimal.ZERO));
        List<Parcela> ps = parcelas(c.id());
        assertEquals(1, ps.size());
        assertEquals(SituacaoParcela.CANCELADA, ps.get(0).getSituacao());
        assertNull(ps.get(0).getDataBaixa());
        assertEquals(1, lancamentoRepository.findAll().stream().filter(x -> x.getUsuario().getId().equals(usuario.getId())).count());
    }

    /** CEN-NOVO-65 */
    @Test
    void cen65_alterarPagamentoAVistaSubstituiParcelaMantendoDataDaCompra() {
        LocalDate dataCompra = hoje.minusDays(20);
        CompraResponse c = confirmar(pedido("100.00", true, pix, null, null, dataCompra));
        compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, dinheiro.getId(), null, null));
        List<Parcela> ps = parcelas(c.id());
        assertEquals(2, ps.size());
        assertEquals(SituacaoParcela.CANCELADA, ps.get(0).getSituacao());
        Parcela nova = ativas(c.id()).get(0);
        assertEquals(SituacaoParcela.BAIXADA, nova.getSituacao());
        assertEquals(dataCompra, nova.getDataBaixa());
        assertEquals(new BigDecimal("100.00"), nova.getValor());
        assertEquals(new BigDecimal("100.00"), lancamentoDe(c.id()).getValorTotal());
        assertTrue(compraBanco(c.id()).getPago());
    }

    @Test
    void alterarPagamentoComParcelaBaixadaPelaUsuariaEBloqueado() {
        CompraResponse c = confirmar(pedido("100.00", true, credito, 2, hoje.plusDays(5)));
        financeiro.baixar(parcelas(c.id()).get(0).getId(), hoje, pix.getId());
        BusinessException e = assertThrows(BusinessException.class,
                () -> compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, pix.getId(), null, null)));
        assertEquals("Desfaça os pagamentos já marcados antes de alterar a forma de pagar.", e.getMessage());
        assertEquals(2, parcelas(c.id()).size());
    }

    @Test
    void alterarDePagoParaCreditoEDeCreditoParaNaoPago() {
        CompraResponse c = confirmar(pedido("100.00", true, pix, null, null));
        compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, credito.getId(), 2, hoje.plusDays(40)));
        assertEquals(2, ativas(c.id()).size());
        assertFalse(compraBanco(c.id()).getPago());
        assertEquals(credito.getId(), compraBanco(c.id()).getMetodoPagamento().getId());
        compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(false, null, null, hoje.plusDays(9)));
        assertEquals(1, ativas(c.id()).size());
        assertEquals(hoje.plusDays(9), ativas(c.id()).get(0).getVencimento());
        assertNull(compraBanco(c.id()).getMetodoPagamento());
        assertNull(compraBanco(c.id()).getParcelas());
    }

    /** CEN-NOVO-66 */
    @Test
    void cen66_compraAntigaSemLancamentoCriaOPrimeiroAPartirDaAlteracao() {
        CompraResponse antiga = confirmar(pedido("100.00", false, null, null, hoje.plusDays(3), hoje.minusDays(60)));
        removerLancamento(antiga.id());
        compraService.atualizarPagamento(antiga.id(), new PagamentoCompraRequest(true, pix.getId(), null, null));
        Parcela p = parcelas(antiga.id()).get(0);
        assertEquals(SituacaoParcela.BAIXADA, p.getSituacao());
        assertEquals(hoje, p.getDataBaixa());

        CompraResponse outra = confirmar(pedido("70.00", false, null, null, hoje.plusDays(3), hoje.minusDays(60)));
        removerLancamento(outra.id());
        compraService.cancelar(outra.id(), new CancelarCompraRequest(null, null));
        assertNull(lancamentoDe(outra.id()));
        assertEquals(StatusCompra.CANCELADA, compraBanco(outra.id()).getStatus());
    }

    private void removerLancamento(UUID compraId) {
        Lancamento l = lancamentoDe(compraId);
        parcelaRepository.deleteAll(parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId()));
        lancamentoRepository.delete(l);
    }

    /** CEN-NOVO-68: o máximo do cartão é 24x; as canceladas pela substituição não contam no teto de 60 ativas. */
    @Test
    void cen68_substituirNaoAcumulaParcelasAtivasEMantemNumeracao() {
        CompraResponse c = confirmar(pedido("240.00", true, credito, 24, hoje.plusDays(5)));
        assertEquals(24, ativas(c.id()).size());
        compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, credito.getId(), 24, hoje.plusDays(6)));
        compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, credito.getId(), 24, hoje.plusDays(7)));
        List<Parcela> todas = parcelas(c.id());
        assertEquals(72, todas.size());
        assertEquals(48, todas.stream().filter(p -> p.getSituacao() == SituacaoParcela.CANCELADA).count());
        List<Parcela> ativas = ativas(c.id());
        assertEquals(24, ativas.size());
        assertEquals(1, ativas.get(0).getNumero());
        assertEquals(24, ativas.get(23).getNumero());
        assertEquals(new BigDecimal("240.00"), lancamentoDe(c.id()).getValorTotal());
    }

    @Test
    void rascunhoNaoGeraLancamentoEConfirmarGera() {
        CompraResponse r = compraService.criarRascunho(pedido("40.00", false, null, null, null));
        assertNull(lancamentoDe(r.id()));
        compraService.confirmar(r.id(), pedido("40.00", false, null, null, hoje.plusDays(1)));
        assertEquals(1, parcelas(r.id()).size());
    }

    @Test
    void pendenteDeRecebimentoJaTemLancamentoESuasParcelasAcompanhamAEdicao() {
        List<CompraItemRequest> itens = List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, new BigDecimal("100.00")));
        CompraRequest pedido = new CompraRequest(hoje, false, null, false, null, null, itens, null, null, null, false, hoje.plusDays(5), hoje.plusDays(7));
        CompraResponse c = compraService.confirmarNova(pedido).compra();
        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, c.status());
        assertEquals(new BigDecimal("100.00"), lancamentoDe(c.id()).getValorTotal());
        List<CompraItemRequest> maior = List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, new BigDecimal("150.00")));
        compraService.atualizarRascunho(c.id(), new CompraRequest(hoje, false, null, false, null, null, maior, null, null, null, false, hoje.plusDays(5), hoje.plusDays(8)));
        assertEquals(new BigDecimal("150.00"), lancamentoDe(c.id()).getValorTotal());
        assertEquals(hoje.plusDays(8), ativas(c.id()).get(0).getVencimento());
        compraService.cancelar(c.id(), new CancelarCompraRequest(null, null));
        assertEquals(SituacaoLancamento.CANCELADO, lancamentoDe(c.id()).getSituacao());
    }

    @Test
    void listaFiltraPagoSemIncluirCanceladas() {
        CompraResponse pagaPix = confirmar(pedido("10.00", true, pix, null, null));
        CompraResponse naoPaga = confirmar(pedido("20.00", false, null, null, hoje.plusDays(3)));
        CompraResponse cancelada = confirmar(pedido("30.00", false, null, null, hoje.plusDays(3)));
        compraService.cancelar(cancelada.id(), new CancelarCompraRequest(null, null));
        var naoPagas = compraService.listarComFiltros(List.of(), List.of(), null, null, List.of(), null, false, false, false,
                PageRequest.of(0, 20)).getContent();
        assertEquals(List.of(naoPaga.id()), naoPagas.stream().map(x -> x.id()).toList());
        var pagas = compraService.listarComFiltros(List.of(), List.of(), null, null, List.of(), null, true, false, false,
                PageRequest.of(0, 20)).getContent();
        assertEquals(List.of(pagaPix.id()), pagas.stream().map(x -> x.id()).toList());
    }

    // --------------------------------------------------------------------------- entrada hostil (regra da casa)

    @Test
    void entradaHostilNuncaViraErroInterno() {
        LocalDate ok = hoje.plusDays(3);
        for (Integer n : new Integer[]{0, -1, 25, 61, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertThrows(BusinessException.class, () -> confirmar(pedido("100.00", true, credito, n, ok)), "parcelas=" + n);
        }
        for (LocalDate v : new LocalDate[]{LocalDate.of(1999, 12, 31), LocalDate.of(2101, 1, 1), LocalDate.MIN, LocalDate.MAX}) {
            assertThrows(BusinessException.class, () -> confirmar(pedido("100.00", false, null, null, v)), "vencimento=" + v);
            assertThrows(BusinessException.class, () -> confirmar(pedido("100.00", true, credito, 2, v)), "vencimento credito=" + v);
        }
        assertThrows(BusinessException.class, () -> confirmar(pedido("100.00", true, null, null, ok)));
        assertThrows(RuntimeException.class, () -> confirmar(pedido("100.00", true, pix, null, ok, LocalDate.of(1900, 1, 1))));
        assertEquals(0, lancamentoRepository.findAll().stream().filter(l -> l.getUsuario().getId().equals(usuario.getId())).count());
    }

    @Test
    void pagamentoDeCompraDeOutraContaNaoExiste() {
        CompraResponse c = confirmar(pedido("100.00", false, null, null, hoje.plusDays(3)));
        Usuario outra = usuarioRepository.save(Usuario.builder().email("outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(outra.getEmail(), null, List.of()));
        assertThrows(com.penseprecifique.api.shared.exception.ResourceNotFoundException.class,
                () -> compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(false, null, null, hoje.plusDays(5))));
        assertThrows(com.penseprecifique.api.shared.exception.ResourceNotFoundException.class,
                () -> compraService.cancelar(c.id(), new CancelarCompraRequest(null, null)));
    }
}
