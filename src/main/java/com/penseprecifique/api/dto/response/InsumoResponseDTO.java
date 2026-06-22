package com.penseprecifique.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InsumoResponseDTO(
        UUID id,
        String nome,
        String marca,
        String unidadeMedida,
        BigDecimal custoUnitario,
        BigDecimal estoqueAtual,
        BigDecimal estoqueMinimo,
        boolean ativo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
