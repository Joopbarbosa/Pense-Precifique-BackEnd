package com.penseprecifique.api.shared.dto.response.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RN-NOVA-39 (V0.18.0, #1166) — um produto do orçamento com insumo não fracionável: o que foi
 * pedido, o rendimento da receita, a quantidade sugerida (menor múltiplo do rendimento que cobre o
 * pedido) e a sobra que ficaria no estoque.
 */
@Getter
@Setter
public class SugestaoQuantidadeProducaoResponse {

    private UUID produtoId;
    private String nomeProduto;
    private BigDecimal quantidadePedida;
    private BigDecimal rendimento;
    private BigDecimal quantidadeSugerida;
    private BigDecimal sobra;
}
