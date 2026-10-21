package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.domain.entity.CompromissoUsuaria;
import com.penseprecifique.api.shared.domain.entity.CompromissoUsuariaExcecao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.VinculoSituacao;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * V0.18.0 (#1039, DT-NOVA-5) — uma ocorrência do compromisso da usuária com tudo resolvido: o que vale é o da exceção,
 * quando ela existe e preenche o campo, ou o do mestre. Série cancelada inteira cancela as ocorrências sem exceção.
 */
record OcorrenciaResolvida(
        CompromissoUsuaria mestre,
        LocalDate dataOriginal,
        LocalDate data,
        String titulo,
        LocalTime horario,
        SituacaoCompromisso situacao,
        String observacaoCancelamento,
        Orcamento orcamento,
        Insumo insumo,
        CompromissoUsuariaExcecao excecao
) {
    static OcorrenciaResolvida de(CompromissoUsuaria m, LocalDate dataOriginal, CompromissoUsuariaExcecao e) {
        if (e == null) {
            boolean serieCancelada = m.getSituacao() == SituacaoCompromisso.CANCELADO;
            return new OcorrenciaResolvida(m, dataOriginal, dataOriginal, m.getTitulo(), m.getHorario(),
                    serieCancelada ? SituacaoCompromisso.CANCELADO : SituacaoCompromisso.PENDENTE,
                    serieCancelada ? m.getObservacaoCancelamento() : null, m.getOrcamento(), m.getInsumo(), null);
        }
        Orcamento orcamento = switch (e.getOrcamentoSituacao()) {
            case HERDA -> m.getOrcamento();
            case DEFINIDO -> e.getOrcamento();
            case REMOVIDO -> null;
        };
        Insumo insumo = switch (e.getInsumoSituacao()) {
            case HERDA -> m.getInsumo();
            case DEFINIDO -> e.getInsumo();
            case REMOVIDO -> null;
        };
        return new OcorrenciaResolvida(m, dataOriginal, e.getNovaData() != null ? e.getNovaData() : dataOriginal,
                e.getTitulo() != null ? e.getTitulo() : m.getTitulo(),
                e.isHorarioProprio() ? e.getHorario() : m.getHorario(), e.getSituacao(), e.getObservacaoCancelamento(),
                orcamento, insumo, e);
    }

    boolean pendente() {
        return situacao == SituacaoCompromisso.PENDENTE;
    }

    static VinculoSituacao situacaoDe(Object vinculo) {
        return vinculo == null ? VinculoSituacao.REMOVIDO : VinculoSituacao.DEFINIDO;
    }
}
