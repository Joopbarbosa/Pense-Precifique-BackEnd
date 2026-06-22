package com.penseprecifique.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record InsumoRequestDTO(

        @NotBlank(message = "O nome do insumo é obrigatório")
        String nome,

        String marca,

        @NotBlank(message = "A unidade de medida é obrigatória")
        String unidadeMedida,

        @DecimalMin(value = "0", message = "O estoque atual não pode ser negativo")
        BigDecimal estoqueAtual,

        @DecimalMin(value = "0", message = "O estoque mínimo não pode ser negativo")
        BigDecimal estoqueMinimo
) {}
