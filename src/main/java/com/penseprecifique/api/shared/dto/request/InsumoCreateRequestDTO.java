package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InsumoCreateRequestDTO(

        @NotBlank(message = "O nome do insumo é obrigatório")
        String nome,

        String marca,

        @NotBlank(message = "A unidade de medida é obrigatória")
        String unidadeMedida,

        Boolean fracionavel,

        Boolean permitirEstoqueNegativo,

        @DecimalMin(value = "0", message = "O estoque mínimo não pode ser negativo")
        BigDecimal estoqueMinimo,

        @NotNull(message = "O preço total da compra inicial é obrigatório")
        @DecimalMin(value = "0.01", message = "O preço total da compra inicial deve ser maior que zero")
        BigDecimal precoTotalCompraInicial,

        @NotNull(message = "A quantidade comprada inicial é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade comprada inicial deve ser maior que zero")
        BigDecimal quantidadeCompradaInicial
) {}
