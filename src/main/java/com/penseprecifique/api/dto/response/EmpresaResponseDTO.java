package com.penseprecifique.api.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmpresaResponseDTO(
        UUID id,
        String nome,
        String email,
        String whatsapp,
        String endereco,
        String logoUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
