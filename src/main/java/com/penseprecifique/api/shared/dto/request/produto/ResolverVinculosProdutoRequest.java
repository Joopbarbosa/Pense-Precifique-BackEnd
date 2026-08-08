package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ResolverVinculosProdutoRequest {

    @NotNull(message = "A ação é obrigatória")
    private AcaoResolucaoVinculo acao;

    @NotNull(message = "A operação a executar após resolver os vínculos é obrigatória")
    private OperacaoPosResolucaoVinculo operacao;

    private List<SubstituicaoVinculoProdutoRequest> substituicoes;
}
