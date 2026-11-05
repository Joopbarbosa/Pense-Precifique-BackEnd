package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest.TipoSinal;
import com.penseprecifique.api.shared.dto.response.orcamento.PagamentoOrcamentoValidadoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PP-528 (RN-NOVA-38/39/42, RN-NOVA-10, CEN-NOVO-45/46/59): regras do passo Pagamento, sem banco. */
class PagamentoOrcamentoValidadorTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 31);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID metodoId = UUID.randomUUID();
    private MetodoPagamentoConfiguravelRepository repo;
    private PagamentoOrcamentoValidador validador;

    @BeforeEach
    void preparar() {
        repo = mock(MetodoPagamentoConfiguravelRepository.class);
        Clock clock = Clock.fixed(HOJE.atTime(10, 0).atZone(ZONA).toInstant(), ZONA);
        validador = new PagamentoOrcamentoValidador(repo, clock);
        metodo(metodoId, true);
    }

    private void metodo(UUID id, boolean ativo) {
        when(repo.findByIdAndUsuarioId(id, usuarioId)).thenReturn(Optional.of(MetodoPagamentoConfiguravel.builder()
                .id(id).tipo(TipoMetodoPagamento.PIX).ativo(ativo).ordem(1).build()));
    }

    private Orcamento orcamento(String total) {
        Orcamento o = new Orcamento();
        o.setTotal(new BigDecimal(total));
        return o;
    }

    private PagamentoOrcamentoRequest req(boolean sinal) {
        PagamentoOrcamentoRequest r = new PagamentoOrcamentoRequest();
        r.setMetodoPagamentoId(metodoId);
        r.setTemSinal(sinal);
        if (sinal) r.setSinalAte(HOJE.plusDays(5));
        return r;
    }

    private BusinessException falha(String total, PagamentoOrcamentoRequest r) {
        return assertThrows(BusinessException.class, () -> validador.validar(orcamento(total), r, usuarioId));
    }

    private PagamentoOrcamentoValidadoResponse ok(String total, PagamentoOrcamentoRequest r) {
        return validador.validar(orcamento(total), r, usuarioId);
    }

    @Test
    void semSinalGeraSoSaldoComVencimentoHoje() {
        var r = ok("100.00", req(false));
        assertFalse(r.temSinal());
        assertEquals(0, new BigDecimal("100.00").compareTo(r.saldo()));
        assertTrue(r.geraParcelaSaldo());
        assertEquals(HOJE, r.vencimentoSaldo());
        assertNull(r.sinalAte());
    }

    @Test
    void sinalSemPercentualUsaCinquentaPorCento() {
        var r = ok("101.00", req(true));
        assertEquals(0, new BigDecimal("50.00").compareTo(r.percentualSinal()));
        assertEquals(0, new BigDecimal("50.50").compareTo(r.valorSinal()));
        assertEquals(0, new BigDecimal("50.50").compareTo(r.saldo()));
    }

    @Test
    void percentualArredondaParaBaixoEmCentavos() {
        var q = req(true);
        q.setPercentualSinal(new BigDecimal("33.33"));
        var r = ok("10.00", q);
        assertEquals(0, new BigDecimal("3.33").compareTo(r.valorSinal()));
        assertEquals(0, new BigDecimal("6.67").compareTo(r.saldo()));
    }

    @Test
    void sinalDeCemPorCentoZeraSaldoESemParcelaDeSaldo() {
        var q = req(true);
        q.setPercentualSinal(new BigDecimal("100"));
        var r = ok("80.00", q);
        assertEquals(0, new BigDecimal("80.00").compareTo(r.valorSinal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.saldo()));
        assertFalse(r.geraParcelaSaldo());
        assertNull(r.vencimentoSaldo());
    }

    @Test
    void sinalPorValorCalculaPercentual() {
        var q = req(true);
        q.setTipoSinal(TipoSinal.VALOR);
        q.setValorSinal(new BigDecimal("25.00"));
        var r = ok("100.00", q);
        assertEquals(0, new BigDecimal("25.00").compareTo(r.percentualSinal()));
        assertEquals(0, new BigDecimal("75.00").compareTo(r.saldo()));
    }

    @Test
    void sinalQueArredondaParaZeroERecusado() {
        var q = req(true);
        q.setPercentualSinal(new BigDecimal("1"));
        BusinessException e = falha("0.50", q);
        assertEquals(PagamentoOrcamentoValidador.MSG_SINAL, e.getMessage());
    }

    @Test
    void percentualForaDaFaixaOuComTresCasasERecusado() {
        for (String p : new String[]{"0", "0.99", "100.01", "-5", "10.123", "1E+999999999", "99999999999999999999"}) {
            var q = req(true);
            q.setPercentualSinal(new BigDecimal(p));
            BusinessException e = falha("100.00", q);
            assertEquals(PagamentoOrcamentoValidador.MSG_SINAL, e.getMessage(), p);
            assertEquals("percentualSinal", e.getCampo(), p);
        }
    }

    @Test
    void valorForaDaFaixaERecusado() {
        for (String v : new String[]{"0", "-1", "100.01", "10.001", "1E+999999999"}) {
            var q = req(true);
            q.setTipoSinal(TipoSinal.VALOR);
            q.setValorSinal(new BigDecimal(v));
            assertEquals(PagamentoOrcamentoValidador.MSG_SINAL, falha("100.00", q).getMessage(), v);
        }
        var sem = req(true);
        sem.setTipoSinal(TipoSinal.VALOR);
        assertEquals(PagamentoOrcamentoValidador.MSG_SINAL, falha("100.00", sem).getMessage());
    }

    @Test
    void totalZeroNaoAceitaSinal() {
        assertEquals(PagamentoOrcamentoValidador.MSG_SINAL, falha("0.00", req(true)).getMessage());
        var r = ok("0.00", req(false));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.saldo()));
    }

    @Test
    void dadosDeSinalSemSinalSaoRecusados() {
        var q = req(false);
        q.setPercentualSinal(new BigDecimal("30"));
        assertEquals("Os dados do sinal só valem quando há sinal.", falha("100.00", q).getMessage());
        var q2 = req(false);
        q2.setSinalAte(HOJE);
        assertEquals("temSinal", falha("100.00", q2).getCampo());
    }

    @Test
    void sinalExigePrazo() {
        var q = req(true);
        q.setSinalAte(null);
        BusinessException e = falha("100.00", q);
        assertEquals("sinalAte", e.getCampo());
        assertEquals("Informe até quando o sinal deve ser pago.", e.getMessage());
    }

    @Test
    void datasAnterioresAHojeOuForaDaFaixaSaoRecusadas() {
        var q = req(true);
        q.setSinalAte(HOJE.minusDays(1));
        assertEquals("sinalAte", falha("100.00", q).getCampo());
        var q2 = req(false);
        q2.setVencimentoSaldo(HOJE.minusDays(1));
        assertEquals("vencimentoSaldo", falha("100.00", q2).getCampo());
        var q3 = req(false);
        q3.setVencimentoSaldo(LocalDate.of(1999, 12, 31));
        assertEquals("vencimentoSaldo", falha("100.00", q3).getCampo());
        var q4 = req(true);
        q4.setSinalAte(LocalDate.of(2101, 1, 1));
        assertEquals("sinalAte", falha("100.00", q4).getCampo());
    }

    @Test
    void dataDeHojeEhAceita() {
        var q = req(true);
        q.setSinalAte(HOJE);
        q.setVencimentoSaldo(HOJE);
        assertEquals(HOJE, ok("100.00", q).sinalAte());
    }

    @Test
    void formaInvalidaTemSempreAMesmaResposta() {
        UUID inativo = UUID.randomUUID();
        metodo(inativo, false);
        UUID inexistenteOuDeOutraEmpresa = UUID.randomUUID();
        when(repo.findByIdAndUsuarioId(inexistenteOuDeOutraEmpresa, usuarioId)).thenReturn(Optional.empty());
        for (UUID id : new UUID[]{null, inativo, inexistenteOuDeOutraEmpresa}) {
            var q = req(false);
            q.setMetodoPagamentoId(id);
            BusinessException e = falha("100.00", q);
            assertEquals(PagamentoOrcamentoValidador.MSG_METODO, e.getMessage());
            assertEquals("metodoPagamentoId", e.getCampo());
        }
    }

    @Test
    void valoresSaemComDuasCasas() {
        var q = req(true);
        q.setPercentualSinal(new BigDecimal("12.5"));
        var r = ok("33.33", q);
        assertEquals(2, r.total().scale());
        assertEquals(2, r.valorSinal().scale());
        assertEquals(2, r.saldo().scale());
        assertEquals(2, r.percentualSinal().scale());
    }
}
