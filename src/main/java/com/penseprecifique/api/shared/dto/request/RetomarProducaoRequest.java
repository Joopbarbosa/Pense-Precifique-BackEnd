package com.penseprecifique.api.shared.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RetomarProducaoRequest {

    // RN-065 — mesma semântica de IniciarProducaoRequest.dividir: null ou false mantém o comportamento
    // anterior (permanece TRAVADA se ainda bloqueado); true divide quando a reverificação ainda bloqueia.
    private Boolean dividir;

    // RN-052 — mesma semântica de IniciarProducaoRequest.confirmarEstoqueNegativoInsumoIds, usada quando
    // retomar() baixa insumo pela primeira vez (trava veio do próprio iniciar() bloqueando antes de baixar).
    private List<UUID> confirmarEstoqueNegativoInsumoIds;
}
