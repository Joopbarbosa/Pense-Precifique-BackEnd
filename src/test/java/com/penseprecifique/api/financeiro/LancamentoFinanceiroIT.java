package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.19.0 (#1172, RN-NOVA-1 a 6, UC-NOVO-1) — criação, listas, baixa, desfazer e cancelamento de lançamentos e parcelas.
 * CEN-NOVO-8, 9, 10, 12, 55 e 68. Datas relativas a hoje (relógio de Brasília).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LancamentoFinanceiroIT {

    @Autowired LancamentoFinanceiroService service;
    @Autowired ParcelaFinanceiraService consulta;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoRepository;
    @Autowired Clock relogioBrasilia;

    private Usuario usuario;
    private MetodoPagamentoConfiguravel metodo;
    private LocalDate hoje;

    private Usuario novaUsuaria() {
        Usuario u = usuarioRepository.save(Usuario.builder()
                .email("fin-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        return u;
    }

    private void entrar(Usuario u) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    private MetodoPagamentoConfiguravel metodoDe(Usuario u, boolean ativo) {
        return metodoRepository.save(MetodoPagamentoConfiguravel.builder().usuario(u).tipo(TipoMetodoPagamento.OUTRO)
                .nome("Pix " + UUID.randomUUID()).ativo(ativo).ordem(1).build());
    }

    private void seed() {
        hoje = LocalDate.now(relogioBrasilia);
        usuario = novaUsuaria();
        metodo = metodoDe(usuario, true);
        entrar(usuario);
    }

    private Lancamento criar(TipoLancamento tipo, String valor, int n, LocalDate primeiro) {
        return service.criar(new NovoLancamento(tipo, "Conta de teste", OrigemLancamento.AVULSO, null, null,
                DivisaoParcelas.dividir(new BigDecimal(valor), n, primeiro)));
    }

    private List<Parcela> parcelas(Lancamento l) {
        return parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
    }

    @Test
    void criaLancamentoComParcelasETotalSomado() {
        seed();
        Lancamento l = criar(TipoLancamento.A_PAGAR, "100.00", 3, hoje.plusDays(5));
        assertEquals(new BigDecimal("100.00"), l.getValorTotal());
        assertEquals(SituacaoLancamento.ATIVO, l.getSituacao());
        List<Parcela> ps = parcelas(l);
        assertEquals(3, ps.size());
        assertEquals(new BigDecimal("33.34"), ps.get(2).getValor());
        assertTrue(ps.stream().allMatch(p -> p.getSituacao() == SituacaoParcela.EM_ABERTO));
        assertEquals(1, ps.get(0).getNumero());
    }

    /** CEN-NOVO-8 — vencida e vence hoje são derivadas pelo relógio, nunca gravadas. */
    @Test
    void vencidaEVenceHojeSaoDerivadas() {
        LocalDate h = LocalDate.of(2030, 6, 15);
        Usuario u = novaUsuaria();
        Lancamento l = Lancamento.builder().usuario(u).tipo(TipoLancamento.A_PAGAR).descricao("x").valorTotal(new BigDecimal("10.00"))
                .origem(OrigemLancamento.AVULSO).build();
        Parcela vencida = Parcela.builder().lancamento(l).usuario(u).numero(1).valor(new BigDecimal("10.00")).vencimento(h.minusDays(5)).build();
        Parcela ontem = Parcela.builder().lancamento(l).usuario(u).numero(1).valor(new BigDecimal("10.00")).vencimento(h.minusDays(1)).build();
        Parcela hojeP = Parcela.builder().lancamento(l).usuario(u).numero(1).valor(new BigDecimal("10.00")).vencimento(h).build();
        Parcela futura = Parcela.builder().lancamento(l).usuario(u).numero(1).valor(new BigDecimal("10.00")).vencimento(h.plusDays(1)).build();
        assertEquals("Vencida há 5 dias", ParcelaFinanceiraService.paraResposta(vencida, 1, h).rotulo());
        assertEquals("Vencida há 1 dia", ParcelaFinanceiraService.paraResposta(ontem, 1, h).rotulo());
        assertEquals("VENCE_HOJE", ParcelaFinanceiraService.paraResposta(hojeP, 1, h).situacaoExibida());
        assertEquals("EM_ABERTO", ParcelaFinanceiraService.paraResposta(futura, 1, h).situacaoExibida());
        assertEquals(SituacaoParcela.EM_ABERTO, vencida.getSituacao());
        assertEquals(5L, ParcelaFinanceiraService.paraResposta(vencida, 1, h).diasEmAtraso());
    }

    @Test
    void listaComVencidasPrimeiroETotais() {
        seed();
        LocalDate de = hoje.minusDays(20);
        LocalDate ate = hoje.plusDays(20);
        criar(TipoLancamento.A_PAGAR, "50.00", 1, hoje.plusDays(3));
        criar(TipoLancamento.A_PAGAR, "20.50", 1, hoje.minusDays(4));
        criar(TipoLancamento.A_RECEBER, "999.00", 1, hoje.minusDays(4));
        ListaParcelasResponse r = consulta.listar(TipoLancamento.A_PAGAR, de, ate, FiltroSituacaoParcela.EM_ABERTO, PageRequest.of(0, 10));
        assertEquals(2, r.itens().size());
        assertEquals("VENCIDA", r.itens().get(0).situacaoExibida());
        assertEquals(new BigDecimal("20.50"), r.itens().get(0).valor());
        assertEquals(new BigDecimal("70.50"), r.totalEmAberto());
        assertEquals(new BigDecimal("20.50"), r.totalVencido());
        assertFalse(r.vazioDeLancamentos());
        ListaParcelasResponse so = consulta.listar(TipoLancamento.A_PAGAR, de, ate, FiltroSituacaoParcela.VENCIDAS, PageRequest.of(0, 10));
        assertEquals(1, so.itens().size());
    }

    @Test
    void usuariaSemLancamentosVeListaVaziaComAviso() {
        seed();
        ListaParcelasResponse r = consulta.listar(TipoLancamento.A_PAGAR, null, null, null, PageRequest.of(0, 10));
        assertTrue(r.itens().isEmpty());
        assertTrue(r.vazioDeLancamentos());
        assertEquals(0, r.totalEmAberto().compareTo(BigDecimal.ZERO));
        assertEquals(0, r.totalVencido().compareTo(BigDecimal.ZERO));
    }

    /** CEN-NOVO-9 — baixar e desfazer a baixa. */
    @Test
    void baixaEDesfazBaixa() {
        seed();
        Lancamento l = criar(TipoLancamento.A_RECEBER, "80.00", 1, hoje.minusDays(2));
        UUID id = parcelas(l).get(0).getId();
        Parcela p = service.baixar(id, null, metodo.getId());
        assertEquals(SituacaoParcela.BAIXADA, p.getSituacao());
        assertEquals(hoje, p.getDataBaixa());
        assertEquals(OrigemBaixa.USUARIA, p.getOrigemBaixa());
        assertEquals("RECEBIDA", consulta.paraResposta(p).situacaoExibida());
        Parcela d = service.desfazerBaixa(id);
        assertEquals(SituacaoParcela.EM_ABERTO, d.getSituacao());
        assertNull(d.getDataBaixa());
        assertNull(d.getMetodoPagamento());
        assertNull(d.getOrigemBaixa());
    }

    /** CEN-NOVO-10 — data futura, parcela já baixada, forma inativa ou de outra conta. */
    @Test
    void baixaInvalidaERecusada() {
        seed();
        Lancamento l = criar(TipoLancamento.A_PAGAR, "30.00", 1, hoje);
        UUID id = parcelas(l).get(0).getId();
        assertThrows(BusinessException.class, () -> service.baixar(id, hoje.plusDays(1), metodo.getId()));
        assertThrows(BusinessException.class, () -> service.baixar(id, LocalDate.of(1999, 1, 1), metodo.getId()));
        assertThrows(BusinessException.class, () -> service.baixar(id, null, null));
        assertThrows(BusinessException.class, () -> service.baixar(id, null, metodoDe(usuario, false).getId()));
        Usuario outra = novaUsuaria();
        MetodoPagamentoConfiguravel alheio = metodoDe(outra, true);
        assertThrows(ResourceNotFoundException.class, () -> service.baixar(id, null, alheio.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.baixar(id, null, UUID.randomUUID()));
        service.baixar(id, hoje, metodo.getId());
        assertThrows(BusinessException.class, () -> service.baixar(id, null, metodo.getId()));
        service.desfazerBaixa(id);
        assertThrows(BusinessException.class, () -> service.desfazerBaixa(id));
        assertEquals(SituacaoParcela.EM_ABERTO, parcelas(l).get(0).getSituacao());
    }

    /** CEN-NOVO-55 — uma usuária nunca enxerga nem altera parcela de outra. */
    @Test
    void isolamentoEntreUsuarias() {
        seed();
        Lancamento l = criar(TipoLancamento.A_PAGAR, "30.00", 1, hoje);
        UUID id = parcelas(l).get(0).getId();
        Usuario outra = novaUsuaria();
        MetodoPagamentoConfiguravel mOutra = metodoDe(outra, true);
        entrar(outra);
        assertThrows(ResourceNotFoundException.class, () -> service.baixar(id, null, mOutra.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.desfazerBaixa(id));
        assertThrows(ResourceNotFoundException.class, () -> service.cancelar(l.getId()));
        assertThrows(ResourceNotFoundException.class, () -> consulta.buscar(id));
        assertTrue(consulta.listar(TipoLancamento.A_PAGAR, hoje.minusDays(5), hoje.plusDays(5), null, PageRequest.of(0, 10)).itens().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x",
                OrigemLancamento.COMPRA, UUID.randomUUID(), null, DivisaoParcelas.dividir(new BigDecimal("10.00"), 1, hoje))));
    }

    /** CEN-NOVO-12 — valores, datas e descrição inválidos na criação. */
    @Test
    void criacaoComDadosInvalidosERecusada() {
        seed();
        List<NovaParcela> ok = DivisaoParcelas.dividir(new BigDecimal("10.00"), 1, hoje);
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "  ", OrigemLancamento.AVULSO, null, null, ok)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "a".repeat(501), OrigemLancamento.AVULSO, null, null, ok)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "a\u0000b", OrigemLancamento.AVULSO, null, null, ok)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(null, "x", OrigemLancamento.AVULSO, null, null, ok)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null, List.of())));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null, null)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.COMPRA, null, null, ok)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, UUID.randomUUID(), null, ok)));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("0.00"), hoje, null)))));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("-1.00"), hoje, null)))));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("1.005"), hoje, null)))));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("10.00"), LocalDate.of(2101, 1, 1), null)))));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("99999999.99"), hoje, null), new NovaParcela(new BigDecimal("1.00"), hoje, null)))));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("10.00"), null, null)))));
    }

    @Test
    void criaComBaixaInicialAutomatica() {
        seed();
        Lancamento l = service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "À vista", OrigemLancamento.AVULSO, null, null,
                List.of(new NovaParcela(new BigDecimal("12.34"), hoje, new NovaParcela.BaixaInicial(hoje, metodo.getId())))));
        Parcela p = parcelas(l).get(0);
        assertEquals(SituacaoParcela.BAIXADA, p.getSituacao());
        assertEquals(OrigemBaixa.AUTOMATICA, p.getOrigemBaixa());
    }

    /** CEN-NOVO-68 — o teto de 60 parcelas conta só as ativas; canceladas ficam fora. */
    @Test
    void cancelarZeraOTotalEMantemHistorico() {
        seed();
        Lancamento l = criar(TipoLancamento.A_PAGAR, "100.00", 4, hoje.plusDays(1));
        List<Parcela> ps = parcelas(l);
        service.baixar(ps.get(0).getId(), null, metodo.getId());
        Lancamento c = service.cancelar(l.getId());
        assertEquals(SituacaoLancamento.CANCELADO, c.getSituacao());
        assertEquals(new BigDecimal("25.00"), c.getValorTotal());
        List<Parcela> depois = parcelas(l);
        assertEquals(SituacaoParcela.BAIXADA, depois.get(0).getSituacao());
        assertEquals(SituacaoParcela.CANCELADA, depois.get(3).getSituacao());
        assertThrows(BusinessException.class, () -> service.baixar(depois.get(1).getId(), null, metodo.getId()));
        assertEquals(new BigDecimal("25.00"), service.cancelar(l.getId()).getValorTotal());
        ListaParcelasResponse r = consulta.listar(TipoLancamento.A_PAGAR, hoje, hoje.plusMonths(6), FiltroSituacaoParcela.TODAS, PageRequest.of(0, 20));
        assertEquals(1, r.itens().size());
        assertEquals(1, r.itens().get(0).totalParcelas());
    }

    @Test
    void sessentaParcelasAtivasSaoAceitasESessentaEUmaNao() {
        seed();
        Lancamento l = criar(TipoLancamento.A_PAGAR, "600.00", 60, hoje);
        assertEquals(60, parcelas(l).size());
        List<NovaParcela> mais = new ArrayList<>(DivisaoParcelas.dividir(new BigDecimal("600.00"), 60, hoje));
        mais.add(new NovaParcela(new BigDecimal("1.00"), hoje, null));
        assertThrows(BusinessException.class, () -> service.criar(new NovoLancamento(TipoLancamento.A_PAGAR, "x", OrigemLancamento.AVULSO, null, null, mais)));
    }

    @Test
    void periodoInvertidoOuForaDaFaixaERecusado() {
        seed();
        assertThrows(BusinessException.class, () -> consulta.listar(TipoLancamento.A_PAGAR, hoje.plusDays(2), hoje, null, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class, () -> consulta.listar(TipoLancamento.A_PAGAR, LocalDate.of(1900, 1, 1), hoje, null, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class, () -> consulta.listar(TipoLancamento.A_PAGAR, hoje, LocalDate.of(2500, 1, 1), null, PageRequest.of(0, 10)));
    }
}
