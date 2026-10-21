package com.penseprecifique.api.shared.dto.response.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoItemCustomizacaoResponse {

    private UUID id;
    private UUID produtoId;
    private String nomeProduto;
    /** #720/RN-NOVA-14 — Produto da customização; nulo quando foi excluído. */
    private UUID destinoId;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
}
