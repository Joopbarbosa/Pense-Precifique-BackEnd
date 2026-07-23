package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ProducaoProdutoResponse {

    private UUID produtoId;
    private String nomeProduto;
    private TipoProduto tipoProduto;
    private BigDecimal quantidade;
    /** #188/RN-NOVA-4 — perda declarada ao finalizar (0 quando não declarada). */
    private BigDecimal quantidadePerdida;
}
