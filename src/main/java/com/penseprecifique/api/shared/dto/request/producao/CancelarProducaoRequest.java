package com.penseprecifique.api.shared.dto.request.producao;

import com.penseprecifique.api.shared.validation.LimitesTexto;
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
    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
    private String justificativa;

    // RN-072 — só relevante para EM_ANDAMENTO/TRAVADA. Nullable — se null (ou item ausente),
    // assume consumo total (quantidade original baixada), sem estorno.
    @Valid
    private List<ConsumoRealRequest> consumoReal;
}
