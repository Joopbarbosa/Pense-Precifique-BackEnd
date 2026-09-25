package com.penseprecifique.api.shared.dto.response.unidademedida;

import java.time.LocalDateTime;
import java.util.UUID;

public record UnidadeMedidaResponseDTO(
        UUID id,
        String nome,
        String sigla,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
