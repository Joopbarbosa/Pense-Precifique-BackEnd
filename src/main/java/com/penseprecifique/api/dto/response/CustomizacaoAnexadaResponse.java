package com.penseprecifique.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CustomizacaoAnexadaResponse {

    private UUID produtoId;
    private String produtoNome;
    private BigDecimal quantidade;
}
