package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** V0.13.0 (#516, DT-NOVA-1) — reestruturado em 2 blocos independentes (mesmo formato de
 * {@code ResolverVinculosProdutoRequest}), porque o Insumo passou a ter 2 tipos de vínculo:
 * ficha técnica (já existia) e componente de Item de Catálogo (novo, RN-NOVA-1 — antes desta versão
 * Insumo nunca podia ser componente de item de catálogo). Cada bloco só é obrigatório no request se
 * o insumo de fato tiver vínculo daquele tipo — mesma regra de {@code ProdutoService#resolverVinculos}. */
public record ResolverVinculosInsumoRequestDTO(

        @NotNull(message = "A operação a executar após resolver os vínculos é obrigatória")
        OperacaoPosResolucaoVinculo operacao,

        @Valid
        ResolucaoVinculoFichaTecnicaInsumoRequestDTO fichaTecnica,

        @Valid
        ResolucaoVinculoCatalogoInsumoRequestDTO catalogo
) {}
