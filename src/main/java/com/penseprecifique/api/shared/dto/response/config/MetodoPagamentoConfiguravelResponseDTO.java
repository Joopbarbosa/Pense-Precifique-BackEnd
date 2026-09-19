package com.penseprecifique.api.shared.dto.response.config;

import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MetodoPagamentoConfiguravelResponseDTO(
        UUID id,
        TipoMetodoPagamento tipo,
        String nome,
        boolean afetaCaixaFisico,
        BigDecimal taxaMaquininha,
        Boolean ativo,
        Integer ordem,
        /** #491 (V0.12.0) — nulo quando o método não parcela. */
        Integer maxParcelas,
        Boolean taxaParcelaUniforme,
        /** Vazio quando a taxa é uniforme (a taxa vigente é `taxaMaquininha`). */
        List<TaxaParcelaResponseDTO> taxasParcela
) {}
