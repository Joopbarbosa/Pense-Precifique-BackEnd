package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * V0.18.0 (DT-NOVA-1) — compromisso como cada fonte o entrega, antes do atraso e da ordenação, que são do
 * {@link AgendaService}. {@code numeroOrigem} desempata (ORC-N, PRD-N, COM-N); no avulso é zero e vale
 * {@code criadoEm}. {@code dataOriginalOcorrencia} só existe em ocorrência de série.
 *
 * @param chave único dentro da resposta (tipo, origem e, em série, a data original)
 * @param feminino true no compromisso de compra: "Atrasada há N dias" (RN-NOVA-4)
 */
public record CompromissoBase(
        String chave,
        TipoCompromisso tipo,
        SituacaoCompromisso situacao,
        String titulo,
        LocalDate data,
        LocalTime horario,
        List<String> marcacoes,
        List<String> avisos,
        UUID referenciaId,
        String identificador,
        int numeroOrigem,
        BigDecimal valor,
        Instant criadoEm,
        LocalDate dataOriginalOcorrencia,
        boolean feminino
) {
    public boolean pendente() {
        return situacao == SituacaoCompromisso.PENDENTE;
    }
}
