package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CancelarProducaoRequest {

    @NotBlank(message = "A justificativa é obrigatória")
    @Size(min = 30, message = "Justificativa deve ter no mínimo 30 caracteres")
    private String justificativa;

    // RN-072 — só relevante para EM_ANDAMENTO/TRAVADA. Nullable — se null (ou item ausente),
    // assume consumo total (quantidade original baixada), sem estorno.
    @Valid
    private List<ConsumoRealRequest> consumoReal;
}
