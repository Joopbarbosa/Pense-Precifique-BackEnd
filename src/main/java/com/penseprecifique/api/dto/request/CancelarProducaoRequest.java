package com.penseprecifique.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelarProducaoRequest {

    @NotBlank(message = "A observação é obrigatória")
    @Size(min = 50, message = "A observação deve ter no mínimo 50 caracteres")
    private String observacao;
}
