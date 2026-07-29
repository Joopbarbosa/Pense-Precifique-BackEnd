package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Frente 2/P-BE-CONSOLIDADO-001 — formato reduzido de item em construção para
 * POST /orcamentos/simular-alertas, sem os demais campos de {@link OrcamentoItemRequest}
 * (precoUnitario, margemAplicada, customizacoes) que não afetam o cálculo de estoque.
 * Origem é XOR: itemCatalogoId (Catálogo) OU produtoId (avulso), validado no Service.
 */
@Getter
@Setter
public class SimularAlertasOrcamentoItemRequest {

    private UUID itemCatalogoId;

    private UUID produtoId;

    @NotNull
    @Min(1)
    private Integer quantidade;
}
