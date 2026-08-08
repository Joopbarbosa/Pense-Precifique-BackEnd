package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ResolucaoVinculoComponenteRequest {

    @NotNull(message = "A ação é obrigatória")
    private AcaoResolucaoVinculo acao;

    private List<SubstituicaoComponenteVinculoRequest> substituicoes;
}
