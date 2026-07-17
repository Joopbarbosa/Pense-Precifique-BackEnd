package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CatalogoRequest {

    @NotBlank(message = "O nome do catálogo é obrigatório")
    private String nome;

    @NotNull(message = "A margem é obrigatória")
    private BigDecimal margem;
}
