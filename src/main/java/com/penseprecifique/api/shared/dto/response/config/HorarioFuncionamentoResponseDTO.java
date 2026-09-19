package com.penseprecifique.api.shared.dto.response.config;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

public record HorarioFuncionamentoResponseDTO(
        Integer diaSemana,
        Boolean fechado,
        @JsonFormat(pattern = "HH:mm") LocalTime horaAbertura,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFechamento
) {}
