package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// RN-NOVA-40 (V0.18.0, #1167) — resposta da usuária à pergunta "Cancelar também a produção PRD-N?",
// uma por produção vinculada ao orçamento que está sendo cancelado.
@Getter
@Setter
public class DecisaoProducaoCancelamentoRequest {

    @NotNull(message = "A produção é obrigatória")
    private UUID producaoId;

    // true = "Sim, cancelar a produção"; false = "Não, manter a produção".
    private boolean cancelar;
}
