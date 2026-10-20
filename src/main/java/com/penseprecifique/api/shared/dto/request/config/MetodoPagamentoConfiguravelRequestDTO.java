package com.penseprecifique.api.shared.dto.request.config;

import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MetodoPagamentoConfiguravelRequestDTO(
        @NotNull(message = "O tipo é obrigatório")
        TipoMetodoPagamento tipo,

        @Size(max = 100, message = "O nome do método de pagamento pode ter no máximo 100 caracteres")
        String nome
) {}
