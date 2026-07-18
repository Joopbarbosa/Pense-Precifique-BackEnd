package com.penseprecifique.api.shared.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RetormarProducaoRequest {

    // RN-065 — mesma semântica de IniciarProducaoRequest.dividir: null ou false mantém o comportamento
    // anterior (permanece TRAVADA se ainda bloqueado); true divide quando a reverificação ainda bloqueia.
    private Boolean dividir;
}
