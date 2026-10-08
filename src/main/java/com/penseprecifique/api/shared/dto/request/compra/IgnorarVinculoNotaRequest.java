package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** O fator é validado em HistoricoVinculoNotaService, com a mensagem explicada (#731). */
public record IgnorarVinculoNotaRequest(@NotNull Boolean ignorar, UUID insumoId, BigDecimal fator) {}
