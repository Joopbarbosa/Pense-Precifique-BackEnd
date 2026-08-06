package com.penseprecifique.api.shared.dto.response.insumo;

import com.penseprecifique.api.shared.domain.enums.TipoExibicaoQuantidade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InsumoResponseDTO(
        UUID id,
        Integer numero,
        String identificador,
        String nome,
        String marca,
        String unidadeMedida,
        boolean fracionavel,
        TipoExibicaoQuantidade tipoExibicaoQuantidade,
        boolean permitirEstoqueNegativo,
        BigDecimal custoUnitario,
        BigDecimal estoqueAtual,
        BigDecimal estoqueMinimo,
        boolean ativo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
