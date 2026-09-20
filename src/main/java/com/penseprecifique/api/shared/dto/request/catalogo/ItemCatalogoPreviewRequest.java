package com.penseprecifique.api.shared.dto.request.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** CAT-013 — preview ao vivo do custo/preço sugerido, sem persistir nada (RN-NOVA-2/3, V0.13.0). */
@Getter
@Setter
public class ItemCatalogoPreviewRequest {

    @NotEmpty(message = "É preciso pelo menos 1 componente")
    @Valid
    private List<ItemCatalogoComponenteRequest> componentes;

    @NotNull(message = "O tempo de produção é obrigatório")
    @Min(value = 0, message = "O tempo de produção não pode ser negativo")
    private Integer tempoProducao;

    private BigDecimal margemLucro;
}
