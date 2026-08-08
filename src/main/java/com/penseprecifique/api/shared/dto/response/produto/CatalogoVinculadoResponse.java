package com.penseprecifique.api.shared.dto.response.produto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CatalogoVinculadoResponse {

    private UUID id;
    private String identificador;
    private String nome;
}
