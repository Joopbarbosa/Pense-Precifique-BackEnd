package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.TipoDesconto;

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
        BigDecimal custoUnitarioPosterior,
        /** #576/RN-NOVA-28 — preço cheio, desconto como digitado e em R$ (linha e parte da nota). */
        BigDecimal precoCheio,
        TipoDesconto descontoTipo,
        BigDecimal descontoInformado,
        BigDecimal descontoLinha,
        BigDecimal descontoNota
) {}
