package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.NotBlank;

public record CancelarVendaCaixaRequestDTO(
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        String cancelamentoMotivo
) {}
