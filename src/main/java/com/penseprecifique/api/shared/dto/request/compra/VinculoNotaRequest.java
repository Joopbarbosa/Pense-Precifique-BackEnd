package com.penseprecifique.api.shared.dto.request.compra;

import java.math.BigDecimal;
import java.util.UUID;

/** Insumo e fator são validados num só lugar, em HistoricoVinculoNotaService, com a mensagem explicada (#731). */
public record VinculoNotaRequest(UUID insumoId, BigDecimal fator) {}
