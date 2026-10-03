package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.StatusListaCompra;

import java.time.LocalDateTime;
import java.util.UUID;

/** #546 (V0.15.0) — linha do histórico de listas geradas. */
public record ListaCompraResumoResponse(UUID id, Integer numero, String identificador, LocalDateTime geradaEm,
                                        long quantidadeItens,
                                        // #596/RN-NOVA-41 — geradaEm nulo em RASCUNHO; createdAt sempre preenchido
                                        StatusListaCompra status, LocalDateTime createdAt) {}
