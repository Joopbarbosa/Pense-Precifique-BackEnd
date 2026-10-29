package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** RN-NOVA-42 (V0.18.0, #1169) — junta duas produções de pedidos diferentes num lote. */
@Getter
@Setter
public class JuntarProducoesRequest {

    @NotNull(message = "Informe a produção a juntar")
    private UUID producaoOutraId;

    // Se null, herda da produção com início mais recente entre as duas.
    private LocalDate dataInicio;

    private LocalDate dataTerminoPrevista;
}
