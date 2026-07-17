package com.penseprecifique.api.shared.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InsumoImpactoResponseDTO(
        UUID insumoId,
        String nomeInsumo,
        String marca,
        String unidadeMedida,
        BigDecimal custoUnitarioAnterior,
        BigDecimal custoUnitarioNovo,
        BigDecimal quantidadeAdicionada
) {}
