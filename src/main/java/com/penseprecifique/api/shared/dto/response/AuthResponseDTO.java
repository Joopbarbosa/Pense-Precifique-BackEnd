package com.penseprecifique.api.shared.dto.response;

import java.util.UUID;

public record AuthResponseDTO(

        String token,
        String tipo,
        UUID usuarioId,
        String email,
        Long expiresIn
) {
    public AuthResponseDTO(String token, UUID usuarioId, String email, Long expiresIn) {
        this(token, "Bearer", usuarioId, email, expiresIn);
    }
}
