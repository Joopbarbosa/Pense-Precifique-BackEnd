package com.penseprecifique.api.shared.dto.response.producao;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RN-NOVA-15 (V0.8.3, #375+308) — lado espelhado de {@code OrcamentoProducaoResponse}: item da
 * seção "Orçamentos vinculados" no Detalhe de Produção. {@code valorTotal} vem de
 * {@code Orcamento.total} — nome do campo no DTO escolhido por legibilidade, não espelha o nome
 * da coluna/entidade (decisão de implementação, ver contrato-producao.md).
 */
@Getter
@Setter
public class ProducaoOrcamentoResponse {

    private UUID orcamentoId;
    private String identificadorOrcamento;
    private StatusOrcamento statusOrcamento;
    private String nomeCliente;
    private BigDecimal valorTotal;
}
