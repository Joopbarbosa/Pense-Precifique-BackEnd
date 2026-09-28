package com.penseprecifique.api.shared.dto.request.compra;

import com.penseprecifique.api.shared.domain.enums.StatusListaCompra;
import jakarta.validation.constraints.NotNull;

/** #596/RN-NOVA-41 (V0.15.0) — troca manual do status da lista de compras. */
public record AlterarStatusListaCompraRequest(
        @NotNull(message = "Escolha o status")
        StatusListaCompra status
) {}
