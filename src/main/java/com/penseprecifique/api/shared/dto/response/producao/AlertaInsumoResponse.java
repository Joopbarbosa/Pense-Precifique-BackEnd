package com.penseprecifique.api.shared.dto.response.producao;

import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AlertaInsumoResponse {

    private String nomeInsumo;
    /** V0.18.0 (#1071) — sigla da unidade do insumo, para o Resumo do dia; nula para produto base. */
    private String siglaUnidade;
    private BigDecimal estoqueAtual;
    private BigDecimal quantidadeNecessaria;
    private SituacaoAlertaInsumo situacao;
}
