package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

/**
 * RN-NOVA-4 (V0.10.0, #336) — contadores por filtro da Lista/Kanban de Produção, agregados no
 * backend. Mesmo padrão já usado por ProdutoContagensResponse.
 */
@Getter
@Setter
public class ProducaoContagensResponse {

    private long total;
    private long aguardandoInicio;
    private long emAndamento;
    private long travada;
    private long finalizada;
    private long cancelada;
    private long naoRealizada;
}
