package com.penseprecifique.api.shared.dto.response.config;

import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;

import java.math.BigDecimal;
import java.util.UUID;

public record MetodoPagamentoConfiguravelResponseDTO(
        UUID id,
        TipoMetodoPagamento tipo,
        String nome,
        boolean afetaCaixaFisico,
        BigDecimal taxaMaquininha,
        Boolean ativo,
        Integer ordem
) {}
