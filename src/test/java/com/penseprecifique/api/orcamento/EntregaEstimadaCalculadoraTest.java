package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** RN-NOVA-8 (V0.18.0, #444) — CEN-NOVO-13 a CEN-NOVO-18 no cálculo da entrega estimada. */
class EntregaEstimadaCalculadoraTest {

    private final EntregaEstimadaCalculadora calculadora = new EntregaEstimadaCalculadora();

    private Orcamento.OrcamentoBuilder base() {
        return Orcamento.builder().dataAprovacao(LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    /** CEN-NOVO-13 — com sinal ativo a contagem parte do sinal pago (05/10 + 10 = 15/10). */
    @Test
    void comSinalAtivoContaDoSinalPago() {
        Orcamento o = base().sinalAtivo(true).prazoProducaoDias(10)
                .dataSinalPago(LocalDateTime.of(2026, 10, 5, 14, 0)).build();
        assertEquals(LocalDate.of(2026, 10, 15), calculadora.calcular(o));
    }

    /** CEN-NOVO-14 — sem sinal a contagem parte da aprovação (01/10 + 10 = 11/10). */
    @Test
    void semSinalContaDaAprovacao() {
        Orcamento o = base().sinalAtivo(false).prazoProducaoDias(10).inicioAssimQueAprovado(true).build();
        assertEquals(LocalDate.of(2026, 10, 11), calculadora.calcular(o));
    }

    /** CEN-NOVO-15 — data de início estimada mais tardia que o sinal pago vale (08/10 + 10 = 18/10). */
    @Test
    void dataDeInicioEstimadaMaisTardiaVale() {
        Orcamento o = base().sinalAtivo(true).prazoProducaoDias(10)
                .dataSinalPago(LocalDateTime.of(2026, 10, 5, 14, 0))
                .inicioAssimQueAprovado(false).dataInicioEstimada(LocalDate.of(2026, 10, 8)).build();
        assertEquals(LocalDate.of(2026, 10, 18), calculadora.calcular(o));
    }

    /** CEN-NOVO-16 — data de início anterior ao sinal pago não vale (08/10 + 5 = 13/10). */
    @Test
    void dataDeInicioAnteriorAoSinalPagoNaoVale() {
        Orcamento o = base().sinalAtivo(true).prazoProducaoDias(5)
                .dataSinalPago(LocalDateTime.of(2026, 10, 8, 10, 0))
                .inicioAssimQueAprovado(false).dataInicioEstimada(LocalDate.of(2026, 10, 6)).build();
        assertEquals(LocalDate.of(2026, 10, 13), calculadora.calcular(o));
    }

    /** "Início assim que aprovado" em Sim ignora a data de início estimada, mesmo que ela esteja preenchida. */
    @Test
    void inicioAssimQueAprovadoIgnoraDataDeInicio() {
        Orcamento o = base().sinalAtivo(false).prazoProducaoDias(10).inicioAssimQueAprovado(true)
                .dataInicioEstimada(LocalDate.of(2026, 10, 20)).build();
        assertEquals(LocalDate.of(2026, 10, 11), calculadora.calcular(o));
    }

    /** CEN-NOVO-17 — sinal ativo ainda não pago: sem entrega estimada. */
    @Test
    void sinalAtivoAindaNaoPagoNaoTemEntrega() {
        Orcamento o = base().sinalAtivo(true).prazoProducaoDias(10).dataSinalPago(null).build();
        assertNull(calculadora.calcular(o));
    }

    /** CEN-NOVO-18 — sem prazo de produção: sem entrega estimada. */
    @Test
    void semPrazoNaoTemEntrega() {
        Orcamento o = base().sinalAtivo(false).prazoProducaoDias(null).build();
        assertNull(calculadora.calcular(o));
    }

    /** Sem sinal e ainda sem aprovação (rascunho/enviado): sem entrega estimada. */
    @Test
    void semAprovacaoNaoTemEntrega() {
        Orcamento o = Orcamento.builder().sinalAtivo(false).prazoProducaoDias(10).dataAprovacao(null).build();
        assertNull(calculadora.calcular(o));
    }

    /** Dias corridos: o prazo atravessa fim de semana e virada de mês sem pular dia (28/02/2027 + 3 = 03/03/2027). */
    @Test
    void contaDiasCorridosAtravessandoMes() {
        Orcamento o = Orcamento.builder().sinalAtivo(false).prazoProducaoDias(3)
                .dataAprovacao(LocalDateTime.of(2027, 2, 28, 8, 0)).build();
        assertEquals(LocalDate.of(2027, 3, 3), calculadora.calcular(o));
    }
}
