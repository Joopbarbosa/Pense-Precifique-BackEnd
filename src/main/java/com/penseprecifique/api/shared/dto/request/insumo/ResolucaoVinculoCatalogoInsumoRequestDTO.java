package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** V0.13.0 (#516, DT-NOVA-1) — bloco de vínculo de Insumo usado como componente de Item de Catálogo,
 * mesmo formato de {@code ResolucaoVinculoCatalogoRequest} (Produto). */
public record ResolucaoVinculoCatalogoInsumoRequestDTO(

        @NotNull(message = "A ação é obrigatória")
        AcaoResolucaoVinculo acao,

        List<SubstituicaoVinculoCatalogoInsumoRequestDTO> substituicoes
) {}
