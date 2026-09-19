package com.penseprecifique.api.shared.dto.request.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;

public record MetodoPagamentoConfiguravelUpdateRequestDTO(
        Boolean ativo,
        BigDecimal taxaMaquininha,
        String nome,

        /** #491 (V0.12.0) — parcelamento, só aceito em CARTAO_CREDITO. */
        @Min(value = 1, message = "A parcela máxima deve ser pelo menos 1")
        @Max(value = 24, message = "A parcela máxima não pode passar de 24")
        Integer maxParcelas,

        /** TRUE: a taxa da maquininha vale para toda parcela. FALSE: exige `taxasParcela` com uma
         *  entrada para cada parcela de 1 até `maxParcelas`. */
        Boolean taxaParcelaUniforme,

        @Valid
        List<TaxaParcelaRequestDTO> taxasParcela
) {}
