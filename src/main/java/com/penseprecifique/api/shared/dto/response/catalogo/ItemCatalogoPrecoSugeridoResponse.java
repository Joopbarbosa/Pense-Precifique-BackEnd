package com.penseprecifique.api.shared.dto.response.catalogo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class ItemCatalogoPrecoSugeridoResponse {

    private BigDecimal precoVendaProduto;
    private Integer quantidadePacote;
    private BigDecimal precoVendaCustomizacoes;
    private BigDecimal precoSugerido;
}
