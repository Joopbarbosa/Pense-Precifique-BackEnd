package com.penseprecifique.api.shared.dto.response.dashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrcamentoResumoDTO {
    private UUID id;
    private Integer numero;
    private String nomeCliente;
    private BigDecimal total;
    private String status;
}
