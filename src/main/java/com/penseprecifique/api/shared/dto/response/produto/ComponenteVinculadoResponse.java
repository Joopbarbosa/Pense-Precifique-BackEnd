package com.penseprecifique.api.shared.dto.response.produto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ComponenteVinculadoResponse {

    private UUID vinculoId;
    private UUID produtoId;
    private String produtoIdentificador;
    private String produtoNome;
}
