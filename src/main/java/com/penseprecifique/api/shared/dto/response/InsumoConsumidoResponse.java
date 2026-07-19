package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class InsumoConsumidoResponse {

    private UUID insumoId;
    private String nomeInsumo;
    private String marca;
    private String unidadeMedida;
    private BigDecimal quantidade;
    private BigDecimal estoqueAntes;
    private boolean estoqueInsuficiente;
    private Boolean fracionavel;
}
