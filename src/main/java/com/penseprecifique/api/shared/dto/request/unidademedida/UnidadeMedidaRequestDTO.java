package com.penseprecifique.api.shared.dto.request.unidademedida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnidadeMedidaRequestDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String nome,

        @NotBlank(message = "A sigla é obrigatória")
        @Size(max = 20, message = "A sigla deve ter no máximo 20 caracteres")
        String sigla
) {}
