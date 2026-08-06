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
 * {@code produtoId}+{@code margemAplicada}+{@code precoUnitario} (produto avulso, sem Catálogo).
 * Validado no {@code OrcamentoService}, não em bean validation, pois a regra é condicional entre campos.
 */
@Getter
@Setter
public class OrcamentoItemRequest {

    private UUID itemCatalogoId;

    private UUID produtoId;

    private BigDecimal margemAplicada;

    /** Preço final do item avulso — pode ser igual ou diferente do preço sugerido pela margem. */
    private BigDecimal precoUnitario;

    @NotNull
    @Min(1)
    private Integer quantidade;

    @Valid
    private List<OrcamentoItemCustomizacaoRequest> customizacoes = new ArrayList<>();
}
