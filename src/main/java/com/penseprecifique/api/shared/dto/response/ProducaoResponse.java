package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ProducaoResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private EstadoProducao estado;
    private LocalDate dataInicio;
    private LocalDate dataTerminoPrevista;
    private String observacoes;
}
