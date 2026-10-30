package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** RN-NOVA-42 (V0.18.0, #1169) — junta duas ou mais produções de pedidos diferentes num lote. */
@Getter
@Setter
public class JuntarProducoesRequest {

    @NotEmpty(message = "Informe ao menos uma produção a juntar")
    @Size(max = 20, message = "Junte no máximo 20 produções de uma vez")
    private List<@NotNull(message = "Produção a juntar inválida") UUID> producaoOutraIds;

    // Se null, herda da produção com início mais recente entre as produções.
    private LocalDate dataInicio;

    private LocalDate dataTerminoPrevista;
}
