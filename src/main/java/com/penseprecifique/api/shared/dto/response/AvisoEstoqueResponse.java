package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class AvisoEstoqueResponse {

    private UUID produtoId;
    private String nomeProduto;
    private BigDecimal estoqueAtual;
    private BigDecimal quantidadeNecessaria;
    private String mensagem;
}
