package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #541 (V0.15.0) — linha da compra. {@code precoUnitario} = preço total ÷ quantidade calculado na
 * hora (4 casas), nulo se faltar um dos dois; {@code precoUnitarioPago} e custos anterior/posterior
 * só existem depois da confirmação.
 */
public record CompraItemResponse(
        UUID id,
        int ordem,
        InsumoRefResponse insumo,
        CadastroRefResponse fornecedor,
        BigDecimal quantidade,
        BigDecimal precoTotal,
        BigDecimal precoUnitario,
        BigDecimal precoUnitarioPago,
        BigDecimal custoUnitarioAnterior,
        BigDecimal custoUnitarioPosterior
) {}
