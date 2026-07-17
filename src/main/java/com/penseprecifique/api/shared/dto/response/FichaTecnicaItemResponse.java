package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FichaTecnicaItemResponse {

    private UUID id;
    private UUID insumoId;
    private String nomeInsumo;
    private String marcaInsumo;
    private String unidadeMedida;
    private Boolean fracionavelInsumo;
    private UUID produtoBaseId;
    private String nomeProdutoBase;
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
    private BigDecimal custoTotal;
}
