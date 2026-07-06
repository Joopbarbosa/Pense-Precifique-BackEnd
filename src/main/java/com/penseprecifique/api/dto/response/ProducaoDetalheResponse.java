package com.penseprecifique.api.dto.response;

import com.penseprecifique.api.domain.enums.StatusProducao;
import com.penseprecifique.api.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProducaoDetalheResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private UUID produtoId;
    private String nomeProduto;
    private TipoProduto tipoProduto;
    private BigDecimal quantidade;
    private LocalDateTime dataProducao;
    private StatusProducao status;
    private String observacaoCancelamento;
    private LocalDateTime dataCancelamento;
    private List<InsumoConsumidoResponse> insumosConsumidos;
}
