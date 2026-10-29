package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** RN-NOVA-42 (V0.18.0, #1169) — quanto de um produto de um pedido está no lote ("Pedidos atendidos"). */
@Getter
@Setter
public class PedidoAtendidoResponse {

    private UUID orcamentoId;
    private String identificadorOrcamento;
    private String nomeCliente;
    private UUID produtoId;
    private String nomeProduto;
    private BigDecimal quantidade;
}
