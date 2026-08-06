package com.penseprecifique.api.shared.dto.request.producao;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class IniciarProducaoRequest {

    // RN-052 — ids dos insumos/produtos-base cujo estoque resultaria negativo (permitirEstoqueNegativo=true)
    // e cuja baixa o usuário já confirmou. Componente com esse resultado fora desta lista gera aviso em vez
    // de baixar (ver ConfirmacaoEstoqueNegativoResponse). Sem efeito sobre RN-059 (permitirEstoqueNegativo=false
    // continua bloqueando incondicionalmente, não contornável por confirmação).
    private List<UUID> confirmarEstoqueNegativoInsumoIds;

    // RN-065 — null ou false: comportamento anterior (bloqueante trava tudo). true: divide a produção
    // em duas (produtos sem bloqueio seguem, produtos bloqueantes travam), original vira NÃO_REALIZADA.
    private Boolean dividir;
}
