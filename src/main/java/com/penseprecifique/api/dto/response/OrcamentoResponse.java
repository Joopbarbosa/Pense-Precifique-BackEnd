package com.penseprecifique.api.dto.response;

import com.penseprecifique.api.domain.enums.StatusOrcamento;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoResponse {

    private UUID id;
    private Integer numero;
    private String nomeCliente;
    private StatusOrcamento status;
    private BigDecimal total;
    private LocalDateTime dataValidade;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
