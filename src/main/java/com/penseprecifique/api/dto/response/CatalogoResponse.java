package com.penseprecifique.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CatalogoResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private String nome;
    private BigDecimal margem;
    private boolean ativo;
    /** Contagem de itens do catálogo — calculado no Service, não existe coluna pra isso. */
    private Integer quantidadeItens;
}
