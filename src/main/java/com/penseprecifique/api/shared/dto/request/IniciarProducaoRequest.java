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
}
