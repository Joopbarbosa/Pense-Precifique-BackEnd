package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** RN-NOVA-42 (V0.18.0, #1169) — pedidos do lote somam mais do que o lote produz; AVISO, nunca bloqueio. */
@Getter
@Setter
public class ExcedenteLoteResponse {

    private UUID producaoId;
    private String identificadorProducao;
    private UUID produtoId;
    private String nomeProduto;
    private BigDecimal quantidadeLote;
    private BigDecimal quantidadePedidos;
    private BigDecimal naoCobertas;
}
