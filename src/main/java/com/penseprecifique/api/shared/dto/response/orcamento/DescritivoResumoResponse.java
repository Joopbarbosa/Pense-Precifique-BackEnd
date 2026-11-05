package com.penseprecifique.api.shared.dto.response.orcamento;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;

import java.time.LocalDateTime;
import java.util.UUID;

/** #382 — linha da lista de Descritivos. */
public record DescritivoResumoResponse(
        UUID id,
        Integer numero,
        String identificador,
        String titulo,
        String nomeCliente,
        StatusOrcamento status,
        int quantidadeOpcoes,
        LocalDateTime createdAt) {
}
