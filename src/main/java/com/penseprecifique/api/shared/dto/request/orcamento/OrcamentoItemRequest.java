package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RN-054 — a origem do item é XOR: {@code itemCatalogoId} (Catálogo) OU
 * {@code produtoId}+{@code precoUnitario} (produto avulso, sem Catálogo).
 * Validado no {@code OrcamentoService}, não em bean validation, pois a regra é condicional entre campos.
 */
@Getter
@Setter
public class OrcamentoItemRequest {

    private UUID itemCatalogoId;

    private UUID produtoId;

    /**
     * ORC-020 (REVISÃO)/RN-NOVA-23 (#313) — margem exibida/editada na calculadora de preço no
     * momento da adição, snapshot gravado em {@code OrcamentoItem.margemAplicada} para as duas
     * origens possíveis (Catálogo ou avulso — nunca ambas, por causa do XOR acima). O Backend só
     * persiste o valor enviado, sem recalcular.
     */
    private BigDecimal margemAplicada;

    /** Preço final do item — pode ser igual ou diferente do preço sugerido pela margem. */
    private BigDecimal precoUnitario;

    @NotNull
    @Min(1)
    private Integer quantidade;

    @Valid
    private List<OrcamentoItemCustomizacaoRequest> customizacoes = new ArrayList<>();
}
