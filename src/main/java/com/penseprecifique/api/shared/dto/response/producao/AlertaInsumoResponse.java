package com.penseprecifique.api.shared.dto.response.producao;

import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AlertaInsumoResponse {

    private String nomeInsumo;
    private BigDecimal estoqueAtual;
    private BigDecimal quantidadeNecessaria;
    private SituacaoAlertaInsumo situacao;
}
