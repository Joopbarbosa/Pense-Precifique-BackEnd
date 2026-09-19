package com.penseprecifique.api.shared.dto.request.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** #488 (V0.12.0) — um dia do horário de funcionamento. `diaSemana` segue ISO-8601 (1=segunda). */
public record HorarioFuncionamentoRequestDTO(
        @NotNull(message = "O dia da semana é obrigatório")
        @Min(value = 1, message = "Dia da semana inválido")
        @Max(value = 7, message = "Dia da semana inválido")
        Integer diaSemana,

        @NotNull(message = "Informe se a empresa fecha neste dia")
        Boolean fechado,

        @JsonFormat(pattern = "HH:mm")
        LocalTime horaAbertura,

        @JsonFormat(pattern = "HH:mm")
        LocalTime horaFechamento
) {}
