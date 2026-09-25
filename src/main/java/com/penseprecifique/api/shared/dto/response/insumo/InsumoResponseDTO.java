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
        // #298 (DT-NOVA-2, V0.14.0) — sigla da UnidadeMedida vinculada (denormalizado, evita quebrar
        // todo consumidor que já lê este campo como texto). unidadeMedidaId é o id real, usado pelo
        // Frontend para pré-selecionar a unidade no dropdown de edição.
        String unidadeMedida,
        UUID unidadeMedidaId,
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
