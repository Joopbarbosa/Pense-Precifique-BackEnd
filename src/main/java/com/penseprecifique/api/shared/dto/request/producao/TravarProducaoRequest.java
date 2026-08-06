package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TravarProducaoRequest {

    @NotBlank(message = "A justificativa é obrigatória")
    @Size(min = 30, message = "Justificativa deve ter no mínimo 30 caracteres")
    private String justificativa;
}
