package com.penseprecifique.api.shared.dto.response.catalogo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CustomizacaoAnexadaResponse {

    private UUID id;
    private UUID produtoId;
    private String produtoNome;
    private BigDecimal quantidade;
    /** #487 (V0.12.0) — preço de venda do produto da customização, para o Caixa montar o preview do
     *  carrinho sem round-trip extra (achado da reabertura de RN-NOVA-1, ver decisoes-caixa.md). */
    private BigDecimal precoVenda;
}
