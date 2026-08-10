package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolverVinculosProdutoRequest {

    @NotNull(message = "A operação a executar após resolver os vínculos é obrigatória")
    private OperacaoPosResolucaoVinculo operacao;

    /** Bloco de vínculos de Catálogo (item de catálogo principal + customização anexada) — obrigatório
     * quando o produto tem vínculo desse tipo, omitido quando não tem. */
    @Valid
    private ResolucaoVinculoCatalogoRequest catalogo;

    /** Bloco de vínculos de componente de ficha técnica (produtoBase em ficha de outro produto) —
     * obrigatório quando o produto tem vínculo desse tipo, omitido quando não tem. */
    @Valid
    private ResolucaoVinculoComponenteRequest componente;
}
