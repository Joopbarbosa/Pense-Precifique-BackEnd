package com.penseprecifique.api.shared.dto.request.catalogo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogoRequest {

    @NotBlank(message = "O nome do catálogo é obrigatório")
    private String nome;
}
