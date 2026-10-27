package com.penseprecifique.api.shared.dto.request.agenda;

import com.penseprecifique.api.shared.domain.enums.RecorrenciaCompromisso;
import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** V0.18.0 (#1039, RN-NOVA-19 e RN-NOVA-20) — POST /agenda/compromissos-usuaria: novo compromisso da usuária, com recorrência opcional. */
public record CompromissoUsuariaRequest(
        @NotBlank(message = "Informe o título")
        @Size(max = 120, message = "Máximo de 120 caracteres")
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String titulo,
        @NotNull(message = "Informe a data")
        LocalDate data,
        LocalTime horario,
        /** Hora de término opcional (#1063); exige horário e deve ser posterior a ele. */
        LocalTime horarioFim,
        UUID orcamentoId,
        UUID insumoId,
        RecorrenciaCompromisso recorrencia,
        List<DayOfWeek> diasSemana,
        Integer quantidadeOcorrencias,
        /** Descrição opcional (#1159), até 500 caracteres. */
        @Size(max = 500, message = "Máximo de 500 caracteres")
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String descricao
) {
    public CompromissoUsuariaRequest(String titulo, LocalDate data, LocalTime horario, LocalTime horarioFim, UUID orcamentoId, UUID insumoId,
                                     RecorrenciaCompromisso recorrencia, List<DayOfWeek> diasSemana, Integer quantidadeOcorrencias) {
        this(titulo, data, horario, horarioFim, orcamentoId, insumoId, recorrencia, diasSemana, quantidadeOcorrencias, null);
    }

    public CompromissoUsuariaRequest(String titulo, LocalDate data, LocalTime horario, UUID orcamentoId, UUID insumoId,
                                     RecorrenciaCompromisso recorrencia, List<DayOfWeek> diasSemana, Integer quantidadeOcorrencias) {
        this(titulo, data, horario, null, orcamentoId, insumoId, recorrencia, diasSemana, quantidadeOcorrencias, null);
    }
}
