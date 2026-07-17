package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
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
    private String identificador;
    private String nomeCliente;
    private StatusOrcamento status;
    private BigDecimal total;
    private LocalDateTime dataValidade;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
