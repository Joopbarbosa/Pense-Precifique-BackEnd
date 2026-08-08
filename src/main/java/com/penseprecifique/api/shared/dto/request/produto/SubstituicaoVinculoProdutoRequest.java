package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.domain.enums.TipoVinculoProduto;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SubstituicaoVinculoProdutoRequest {

    @NotNull(message = "O tipo do vínculo é obrigatório")
    private TipoVinculoProduto tipo;

    @NotNull(message = "O id do vínculo é obrigatório")
    private UUID vinculoId;

    @NotNull(message = "O novo produto é obrigatório")
    private UUID novoProdutoId;
}
