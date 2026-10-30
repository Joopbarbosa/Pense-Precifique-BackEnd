package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * RN-NOVA-8 (V0.18.0, #444, DT-NOVA-3) — único lugar que calcula a entrega estimada do orçamento.
 * O prazo é contado em dias corridos a partir de um ponto de partida automático: a data do sinal pago
 * (com sinal ativo) ou a data de aprovação (sem sinal). Com "Início assim que aprovado" em Não e uma
 * data de início estimada, a contagem começa na mais tardia entre o ponto de partida e essa data.
 * Sem prazo de produção, ou com sinal ativo ainda não pago, não há entrega estimada.
 */
@Component
public class EntregaEstimadaCalculadora {

    public LocalDate calcular(Orcamento orcamento) {
        Integer prazo = orcamento.getPrazoProducaoDias();
        if (prazo == null) {
            return null;
        }
        LocalDate partida = pontoDePartida(orcamento);
        if (partida == null) {
            return null;
        }
        if (Boolean.FALSE.equals(orcamento.getInicioAssimQueAprovado()) && orcamento.getDataInicioEstimada() != null
                && orcamento.getDataInicioEstimada().isAfter(partida)) {
            partida = orcamento.getDataInicioEstimada();
        }
        return partida.plusDays(prazo);
    }

    /** V0.18.0 (#1181) — dia em que a contagem do prazo começa (já considerando a data de início estimada); null se ainda não há. */
    public LocalDate pontoDeContagem(Orcamento orcamento) {
        LocalDate partida = pontoDePartida(orcamento);
        if (partida != null && Boolean.FALSE.equals(orcamento.getInicioAssimQueAprovado()) && orcamento.getDataInicioEstimada() != null
                && orcamento.getDataInicioEstimada().isAfter(partida)) {
            return orcamento.getDataInicioEstimada();
        }
        return partida;
    }

    private LocalDate pontoDePartida(Orcamento orcamento) {
        if (Boolean.TRUE.equals(orcamento.getSinalAtivo())) {
            return orcamento.getDataSinalPago() == null ? null : orcamento.getDataSinalPago().toLocalDate();
        }
        return orcamento.getDataAprovacao() == null ? null : orcamento.getDataAprovacao().toLocalDate();
    }
}
