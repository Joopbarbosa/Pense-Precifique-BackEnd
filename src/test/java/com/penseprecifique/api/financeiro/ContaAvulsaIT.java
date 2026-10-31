package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** PP-523: conta avulsa, repetição mensal e alterações por ocorrência. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContaAvulsaIT {
    @Autowired LancamentoFinanceiroService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired ParcelaRepository parcelas;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;
    @Autowired Clock relogio;
    @Autowired CompraRepository compras;
    @Autowired LancamentoRepository lancamentos;

    private Usuario entrar() {
        Usuario u = usuarios.save(Usuario.builder().email("avulsa-" + UUID.randomUUID() + "@teste.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
        return u;
    }

    private ContaAvulsaRequest pedido(String valor, LocalDate data, Integer meses) {
        return new ContaAvulsaRequest(TipoLancamento.A_PAGAR, "Aluguel", new BigDecimal(valor), data,
                null, meses, true, false, null, null);
    }

    @Test
    void criaUmaParcelaPorMesEVoltaAoDia31() {
        entrar();
        Lancamento l = service.criarContaAvulsa(pedido("50.00", LocalDate.of(2027, 1, 31), 3));
        List<Parcela> ps = parcelas.findByLancamentoIdOrderByNumeroAsc(l.getId());
        assertEquals(new BigDecimal("150.00"), l.getValorTotal());
        assertEquals(List.of(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)), ps.stream().map(Parcela::getVencimento).toList());
        assertTrue(l.isForaProjecao());
    }

    @Test
    void mesesForaDoLimiteEValorTotalExcessivoNaoCriamLancamento() {
        Usuario u = entrar();
        for (int n : List.of(0, 1, 25)) assertThrows(BusinessException.class, () -> service.criarContaAvulsa(pedido("10.00", LocalDate.of(2027, 1, 1), n)));
        assertThrows(BusinessException.class, () -> service.criarContaAvulsa(pedido("99999999.99", LocalDate.of(2027, 1, 1), 2)));
        assertFalse(lancamentos.existsByUsuarioId(u.getId()));
    }

    @Test
    void editaSomenteOcorrenciasAbertasEExcluiSemApagarPaga() {
        Usuario u = entrar();
        MetodoPagamentoConfiguravel metodo = metodos.save(MetodoPagamentoConfiguravel.builder().usuario(u).tipo(TipoMetodoPagamento.OUTRO)
                .nome("PIX " + UUID.randomUUID()).ativo(true).ordem(1).build());
        LocalDate vencimentoInicial = LocalDate.now(relogio).minusDays(1);
        Lancamento l = service.criarContaAvulsa(pedido("100.00", vencimentoInicial, 3));
        List<Parcela> ps = parcelas.findByLancamentoIdOrderByNumeroAsc(l.getId());
        service.baixar(ps.get(0).getId(), vencimentoInicial, metodo.getId());
        service.editarOcorrencia(ps.get(1).getId(), new EditarOcorrenciaRequest(null, new BigDecimal("110.00"), null, null, null), true);
        assertEquals(new BigDecimal("100.00"), parcelas.findById(ps.get(0).getId()).orElseThrow().getValor());
        assertEquals(new BigDecimal("110.00"), parcelas.findById(ps.get(2).getId()).orElseThrow().getValor());
        assertFalse(service.preverExclusaoOcorrencia(ps.get(1).getId(), true).excluiContaInteira());
        Lancamento parcial = service.excluirOcorrencia(ps.get(1).getId(), true);
        assertEquals(SituacaoParcela.BAIXADA, parcelas.findById(ps.get(0).getId()).orElseThrow().getSituacao());
        assertEquals(SituacaoLancamento.ATIVO, parcial.getSituacao());
    }

    @Test
    void serieJaPagaBaixaSomentePrimeiraOcorrencia() {
        Usuario u = entrar();
        MetodoPagamentoConfiguravel metodo = metodos.save(MetodoPagamentoConfiguravel.builder().usuario(u).tipo(TipoMetodoPagamento.OUTRO)
                .nome("PIX " + UUID.randomUUID()).ativo(true).ordem(1).build());
        LocalDate ontem = LocalDate.now(relogio).minusDays(1);
        ContaAvulsaRequest req = new ContaAvulsaRequest(TipoLancamento.A_PAGAR, "Internet", new BigDecimal("100.00"), ontem,
                null, 3, false, true, ontem, metodo.getId());
        Lancamento l = service.criarContaAvulsa(req);
        List<Parcela> ps = parcelas.findByLancamentoIdOrderByNumeroAsc(l.getId());
        assertEquals(new BigDecimal("300.00"), l.getValorTotal());
        assertEquals(SituacaoParcela.BAIXADA, ps.get(0).getSituacao());
        assertEquals(ontem, ps.get(0).getDataBaixa());
        assertTrue(ps.subList(1, 3).stream().allMatch(p -> p.getSituacao() == SituacaoParcela.EM_ABERTO));
    }

    @Test
    void limiteConsideraSomaDepoisDaEdicaoEUltimaExclusaoCancelaSemRestantes() {
        entrar();
        Lancamento l = service.criarContaAvulsa(pedido("49999999.99", LocalDate.of(2027, 1, 10), 2));
        assertEquals(new BigDecimal("99999999.98"), l.getValorTotal());
        List<Parcela> ps = parcelas.findByLancamentoIdOrderByNumeroAsc(l.getId());
        assertThrows(BusinessException.class, () -> service.editarOcorrencia(ps.get(0).getId(),
                new EditarOcorrenciaRequest(null, new BigDecimal("99999999.99"), null, null, null), false));
        assertEquals(new BigDecimal("49999999.99"), parcelas.findById(ps.get(0).getId()).orElseThrow().getValor());
        assertTrue(service.preverExclusaoOcorrencia(ps.get(0).getId(), true).excluiContaInteira());
        Lancamento cancelado = service.excluirOcorrencia(ps.get(0).getId(), true);
        assertEquals(SituacaoLancamento.CANCELADO, cancelado.getSituacao());
    }

    @Test
    void compraNaoPodeSerAlteradaNemExcluidaPeloFinanceiro() {
        Usuario u = entrar();
        Compra compra = compras.save(Compra.builder().usuario(u).numero(900001).dataCompra(LocalDate.now(relogio)).build());
        Lancamento l = lancamentos.save(Lancamento.builder().usuario(u).tipo(TipoLancamento.A_PAGAR).descricao("Compra protegida")
                .valorTotal(new BigDecimal("25.00")).origem(OrigemLancamento.COMPRA).compra(compra).build());
        Parcela p = parcelas.save(Parcela.builder().lancamento(l).usuario(u).numero(1).valor(new BigDecimal("25.00"))
                .vencimento(LocalDate.now(relogio).plusDays(2)).build());
        BusinessException editar = assertThrows(BusinessException.class, () -> service.editarOcorrencia(p.getId(),
                new EditarOcorrenciaRequest("Outro nome", null, null, null, null), false));
        assertEquals("Este valor vem de uma compra; altere por lá.", editar.getMessage());
        BusinessException excluir = assertThrows(BusinessException.class, () -> service.excluirOcorrencia(p.getId(), false));
        assertEquals(editar.getMessage(), excluir.getMessage());
    }
}
