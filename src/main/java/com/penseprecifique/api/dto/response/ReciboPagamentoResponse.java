package com.penseprecifique.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ReciboPagamentoResponse {

    private UUID id;
    private UUID orcamentoId;
    private Integer numeroOrcamento;
    private LocalDateTime dataPagamento;
    private BigDecimal valorTotal;
    private BigDecimal valorSinalPago;
    private BigDecimal valorRestantePago;
    private BigDecimal totalQuitado;
    private LocalDateTime createdAt;
}
