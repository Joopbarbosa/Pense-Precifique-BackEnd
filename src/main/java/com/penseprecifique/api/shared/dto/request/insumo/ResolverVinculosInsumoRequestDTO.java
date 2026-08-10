package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ResolverVinculosInsumoRequestDTO(

        @NotNull(message = "A ação é obrigatória")
        AcaoResolucaoVinculo acao,

        @NotNull(message = "A operação a executar após resolver os vínculos é obrigatória")
        OperacaoPosResolucaoVinculo operacao,

        List<SubstituicaoInsumoRequestDTO> substituicoes
) {}
