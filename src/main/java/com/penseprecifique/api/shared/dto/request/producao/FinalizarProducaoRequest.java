package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * #188/RN-NOVA-4/UC-NOVA-3 — perdas opcionais por produto ao finalizar a produção. Produto ausente
 * da lista (ou request nulo/vazio) = sem perda declarada, comportamento anterior preservado
 * (incrementa a quantidade planejada por completo).
 */
@Getter
@Setter
public class FinalizarProducaoRequest {

    @Valid
    private List<PerdaProducaoRequest> perdas;
}
