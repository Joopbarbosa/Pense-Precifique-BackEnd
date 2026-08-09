package com.penseprecifique.api.shared.dto.response.catalogo;

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
    /** #238 — tag global fracionável/estoque negativo/estoque atual, mesmo padrão de ProdutoResponse. */
    private boolean algumInsumoNaoFracionavel;
    private boolean permitirEstoqueNegativo;
    private BigDecimal estoqueAtual;
}
