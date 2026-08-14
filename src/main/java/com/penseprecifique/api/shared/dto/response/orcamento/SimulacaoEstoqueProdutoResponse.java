package com.penseprecifique.api.shared.dto.response.orcamento;

import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #218 (RN-NOVA-8/9) — resposta de POST /orcamentos/simular-alertas, dedicada a Orçamento (nível
 * Produto, não Insumo). Substitui o reaproveitamento anterior de {@code AlertaInsumoResponse}
 * (DTO de Produção): aquele shape não expunha {@code permitirEstoqueNegativo} e reaproveitava o
 * campo "nomeInsumo" para o nome do Produto vendido, confuso para quem consome fora do contexto
 * de Produção. Mesmo enum {@link SituacaoAlertaInsumo} (estrutura de situação já validada).
 */
@Getter
@Setter
public class SimulacaoEstoqueProdutoResponse {

    private UUID produtoId;
    private String nomeProduto;
    private BigDecimal estoqueAtual;
    private BigDecimal quantidadeNecessaria;
    private boolean permitirEstoqueNegativo;
    private SituacaoAlertaInsumo situacao;
}
