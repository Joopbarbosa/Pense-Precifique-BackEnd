package com.penseprecifique.api.shared.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class PrecoSugeridoResponse {

    private BigDecimal custoUnitario;
    private BigDecimal margem;
    private BigDecimal precoSugerido;
}
