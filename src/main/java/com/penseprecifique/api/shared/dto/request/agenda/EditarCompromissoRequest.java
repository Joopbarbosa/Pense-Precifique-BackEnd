package com.penseprecifique.api.shared.dto.request.agenda;

import com.penseprecifique.api.shared.domain.enums.EscopoEdicaoCompromisso;
import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * V0.18.0 (#1039, RN-NOVA-21) — editar título, data, horário e vínculos. É o estado completo desejado: horário, orçamento e
 * insumo nulos significam "sem". Na série inteira a data não muda (só por ocorrência) e é ignorada.
 */
public record EditarCompromissoRequest(
        EscopoEdicaoCompromisso escopo,
        @NotBlank(message = "Informe o título")
        @Size(max = 120, message = "Máximo de 120 caracteres")
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String titulo,
        LocalDate data,
        LocalTime horario,
        /** Hora de término opcional (#1063); exige horário e deve ser posterior a ele. */
        LocalTime horarioFim,
        UUID orcamentoId,
        UUID insumoId
) {
    public EditarCompromissoRequest(EscopoEdicaoCompromisso escopo, String titulo, LocalDate data, LocalTime horario, UUID orcamentoId, UUID insumoId) {
        this(escopo, titulo, data, horario, null, orcamentoId, insumoId);
    }
}
