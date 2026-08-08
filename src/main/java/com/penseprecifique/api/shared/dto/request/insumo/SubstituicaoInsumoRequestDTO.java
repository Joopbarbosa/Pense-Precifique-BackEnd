package com.penseprecifique.api.shared.dto.request.insumo;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubstituicaoInsumoRequestDTO(

        @NotNull(message = "O produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "O novo insumo é obrigatório")
        UUID novoInsumoId
) {}
