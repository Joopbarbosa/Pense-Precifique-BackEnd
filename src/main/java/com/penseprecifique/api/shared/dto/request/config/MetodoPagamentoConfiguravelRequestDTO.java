package com.penseprecifique.api.shared.dto.request.config;

import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import jakarta.validation.constraints.NotNull;

public record MetodoPagamentoConfiguravelRequestDTO(
        @NotNull(message = "O tipo é obrigatório")
        TipoMetodoPagamento tipo,

        String nome
) {}
