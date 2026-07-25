package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class AvisoEstoqueNegativoResponse {

    private UUID componenteId;
    private String nome;
    private BigDecimal estoqueAtual;
    private BigDecimal quantidadeNecessaria;
    private String mensagem;
}
