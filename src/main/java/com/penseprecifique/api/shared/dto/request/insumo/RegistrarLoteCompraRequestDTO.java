package com.penseprecifique.api.shared.dto.request.insumo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public record RegistrarLoteCompraRequestDTO(

        // Opcional — default: agora (resolvido no service)
        LocalDateTime dataCompra,

        @NotEmpty(message = "O lote deve conter pelo menos um insumo")
        @Valid
        List<ItemLoteCompraRequestDTO> itens
) {}
