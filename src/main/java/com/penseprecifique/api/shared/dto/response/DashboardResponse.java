package com.penseprecifique.api.shared.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private Long totalOrcamentos;
    private Long orcamentosPendentes;
    private BigDecimal receitaMes;
    private BigDecimal receitaTotal;
    private List<ProdutoVendidoDTO> produtosMaisVendidos;
    private List<OrcamentoResumoDTO> orcamentosRecentes;
}
