package com.penseprecifique.api.shared.dto.response.insumo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ImpactoAgregadoResponseDTO(
        UUID loteId,
        LocalDateTime dataCompra,
        List<InsumoImpactoResponseDTO> insumosAtualizados,
        // Placeholder para épicos futuros — sempre lista vazia por ora
        List<Object> produtosAfetados
) {}
