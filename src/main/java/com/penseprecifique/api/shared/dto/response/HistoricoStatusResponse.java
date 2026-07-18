package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HistoricoStatusResponse {

    private EstadoProducao statusAnterior;
    private EstadoProducao statusNovo;
    private LocalDateTime dataTransicao;
    private String justificativa;
    private OrigemHistoricoStatus origem;
}
