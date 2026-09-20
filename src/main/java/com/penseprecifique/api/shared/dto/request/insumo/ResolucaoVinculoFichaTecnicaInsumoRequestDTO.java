package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** V0.13.0 (#516) — bloco de vínculo de ficha técnica (uso já existente antes desta versão),
 * extraído para bloco próprio quando o Insumo passou a ter um 2º tipo de vínculo (catálogo). */
public record ResolucaoVinculoFichaTecnicaInsumoRequestDTO(

        @NotNull(message = "A ação é obrigatória")
        AcaoResolucaoVinculo acao,

        List<SubstituicaoInsumoRequestDTO> substituicoes
) {}
