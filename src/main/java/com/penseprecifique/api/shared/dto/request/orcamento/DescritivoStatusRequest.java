package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import jakarta.validation.constraints.NotNull;

/** #382 — muda o status do Descritivo (só RASCUNHO → ENVIADO e RASCUNHO/ENVIADO → CANCELADO). */
public record DescritivoStatusRequest(@NotNull(message = "Informe o status.") StatusOrcamento status) {
}
