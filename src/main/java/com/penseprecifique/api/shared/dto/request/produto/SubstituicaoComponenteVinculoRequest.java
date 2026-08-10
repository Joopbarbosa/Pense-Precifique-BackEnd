package com.penseprecifique.api.shared.dto.request.produto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SubstituicaoComponenteVinculoRequest {

    @NotNull(message = "O id do vínculo é obrigatório")
    private UUID vinculoId;

    @NotNull(message = "O novo produto é obrigatório")
    private UUID novoProdutoId;
}
