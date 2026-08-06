package com.penseprecifique.api.shared.dto.response.producao;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ProducaoResumoResponse {

    private UUID id;
    private String identificador;
    private EstadoProducao estado;
}
