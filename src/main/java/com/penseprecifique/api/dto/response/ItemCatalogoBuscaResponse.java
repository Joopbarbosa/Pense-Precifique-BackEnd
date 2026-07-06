package com.penseprecifique.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ItemCatalogoBuscaResponse {

    private UUID id;
    private String nomeProduto;
    private BigDecimal precoVenda;
    private String catalogoNome;
    private Integer catalogoNumero;
}
