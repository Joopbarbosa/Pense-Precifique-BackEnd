package com.penseprecifique.api.shared.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String email,
        Boolean ativo,
        LocalDateTime createdAt
) {}
