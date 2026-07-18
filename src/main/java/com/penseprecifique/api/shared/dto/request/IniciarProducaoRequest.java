package com.penseprecifique.api.shared.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class IniciarProducaoRequest {

    // Reservado para uso futuro — hoje sem efeito: permitirEstoqueNegativo=false do cadastro
    // bloqueia incondicionalmente (RN-059), não é contornável por confirmação do request.
    private List<UUID> confirmarEstoqueNegativoInsumoIds;

    // RN-065 — null ou false: comportamento anterior (bloqueante trava tudo). true: divide a produção
    // em duas (produtos sem bloqueio seguem, produtos bloqueantes travam), original vira NÃO_REALIZADA.
    private Boolean dividir;
}
