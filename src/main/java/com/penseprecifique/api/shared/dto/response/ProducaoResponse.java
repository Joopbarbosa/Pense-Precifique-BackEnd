package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.StatusProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ProducaoResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private UUID produtoId;
    private String nomeProduto;
    private TipoProduto tipoProduto;
    private BigDecimal quantidade;
    private LocalDateTime dataProducao;
    private StatusProducao status;
}
