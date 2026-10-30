package com.penseprecifique.api.shared.dto.response.agenda;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.penseprecifique.api.shared.domain.enums.OrigemCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * V0.18.0 (DT-NOVA-2) — compromisso da Agenda já pronto para exibir. O atraso (RN-NOVA-4 v2) é calculado no servidor:
 * o frontend só mostra {@code atrasado}, {@code diasAtraso} e {@code minutosAtraso}. {@code dataExibicao} é a data da
 * origem: o atrasado fica no seu dia, como um compromisso normal (#1060).
 */
public record CompromissoResponse(
        /** Identificador estável do compromisso (tipo + origem, e a data original nas ocorrências de série). */
        String chave,
        TipoCompromisso tipo,
        OrigemCompromisso origem,
        SituacaoCompromisso situacao,
        String titulo,
        LocalDate dataOrigem,
        /** Só nas ocorrências do compromisso da usuária: identifica a ocorrência nas ações (concluir, editar, cancelar). */
        LocalDate dataOriginalOcorrencia,
        LocalDate dataExibicao,
        @JsonFormat(pattern = "HH:mm") LocalTime horario,
        /** Hora de término do compromisso avulso (#1063); nula = o bloco dura 1 hora na grade. */
        @JsonFormat(pattern = "HH:mm") LocalTime horarioFim,
        boolean diaInteiro,
        boolean atrasado,
        int diasAtraso,
        /** Atraso por horário (compromisso de hoje com a hora já passada): minutos desde o horário; zero no atraso por data. */
        int minutosAtraso,
        /** Marcações curtas ("Travada"); a tela só as exibe. */
        List<String> marcacoes,
        /** Avisos do compromisso (o de estouro de entrega da produção, RN-NOVA-17). */
        List<String> avisos,
        UUID referenciaId,
        /** "ORC-42", "PRD-12", "COM-7"; nulo no compromisso avulso. */
        String identificador,
        BigDecimal valor
) {}
