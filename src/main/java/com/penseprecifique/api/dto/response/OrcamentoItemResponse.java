package com.penseprecifique.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoItemResponse {

    private UUID id;
    private UUID produtoId;
    private String nomeProduto;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
    private List<OrcamentoItemCustomizacaoResponse> customizacoes;
}
