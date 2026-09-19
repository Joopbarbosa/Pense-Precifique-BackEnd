package com.penseprecifique.api.shared.dto.response.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EmpresaResponseDTO(
        UUID id,
        String nome,
        String email,
        String whatsapp,
        String endereco,
        String logoUrl,
        /** #488 (V0.12.0) — ordenado por dia da semana; vazio quando nunca foi configurado. */
        List<HorarioFuncionamentoResponseDTO> horarios,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
