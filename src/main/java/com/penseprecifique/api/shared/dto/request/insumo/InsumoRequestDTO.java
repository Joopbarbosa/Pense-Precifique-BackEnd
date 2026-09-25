package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.TipoExibicaoQuantidade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InsumoRequestDTO(

        @NotBlank(message = "O nome do insumo é obrigatório")
        String nome,

        String marca,

        // #298 (DT-NOVA-2, V0.14.0) — antes String unidadeMedida (texto livre); agora referencia
        // uma UnidadeMedida já cadastrada em Configurações.
        @NotNull(message = "A unidade de medida é obrigatória")
        UUID unidadeMedidaId,

        Boolean fracionavel,

        TipoExibicaoQuantidade tipoExibicaoQuantidade,

        Boolean permitirEstoqueNegativo,

        @DecimalMin(value = "0", message = "O estoque atual não pode ser negativo")
        BigDecimal estoqueAtual,

        @DecimalMin(value = "0", message = "O estoque mínimo não pode ser negativo")
        BigDecimal estoqueMinimo
) {}
