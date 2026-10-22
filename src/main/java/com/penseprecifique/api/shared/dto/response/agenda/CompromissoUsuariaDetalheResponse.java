package com.penseprecifique.api.shared.dto.response.agenda;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.penseprecifique.api.shared.domain.enums.RecorrenciaCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** V0.18.0 (#1039) — uma ocorrência do compromisso da usuária, com os vínculos resolvidos (RN-NOVA-6, RN-NOVA-7). */
public record CompromissoUsuariaDetalheResponse(
        UUID id,
        String titulo,
        /** Data original da ocorrência: identifica a ocorrência nas ações. */
        LocalDate dataOriginal,
        /** Data em que a ocorrência está hoje (muda quando ela foi movida). */
        LocalDate data,
        @JsonFormat(pattern = "HH:mm") LocalTime horario,
        @JsonFormat(pattern = "HH:mm") LocalTime horarioFim,
        SituacaoCompromisso situacao,
        String observacaoCancelamento,
        boolean serie,
        RecorrenciaCompromisso recorrencia,
        List<DayOfWeek> diasSemana,
        Integer quantidadeOcorrencias,
        VinculoOrcamento orcamento,
        VinculoInsumo insumo
) {
    public record VinculoOrcamento(UUID id, String identificador, String cliente) {}

    /** {@code removido}: o insumo foi inativado ou excluído depois do vínculo ("Insumo removido" no lugar do link). */
    public record VinculoInsumo(UUID id, String nome, boolean removido) {}
}
