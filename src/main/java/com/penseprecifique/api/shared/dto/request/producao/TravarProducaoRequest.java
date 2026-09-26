package com.penseprecifique.api.shared.dto.request.producao;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TravarProducaoRequest {

    @NotBlank(message = "A justificativa é obrigatória")
    @Size(min = 30, message = "Justificativa deve ter no mínimo 30 caracteres")
    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
    private String justificativa;
}
