package com.penseprecifique.api.shared.dto.request.config;

import java.math.BigDecimal;

public record MetodoPagamentoConfiguravelUpdateRequestDTO(
        Boolean ativo,
        BigDecimal taxaMaquininha,
        String nome
) {}
