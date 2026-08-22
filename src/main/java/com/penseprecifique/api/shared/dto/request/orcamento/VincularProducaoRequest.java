package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VincularProducaoRequest {

    @NotNull(message = "O id da produção é obrigatório")
    private UUID producaoId;
}
