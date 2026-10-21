package com.penseprecifique.api.shared.dto.response.catalogo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V0.18.0 (#961, RN-NOVA-28) — uma mudança do preço sugerido do item de Catálogo: valor anterior e novo, causa
 * ({@code CausaPrecoSugerido}) e a referência do evento (ex.: COM-12).
 */
public record HistoricoPrecoItemResponse(
        UUID id,
        BigDecimal valorAnterior,
        BigDecimal valorNovo,
        String causa,
        String referenciaTexto,
        UUID referenciaId,
        LocalDateTime criadoEm
) {}
