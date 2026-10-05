package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.domain.enums.RegraPrecoReferencia;
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
        BigDecimal estoqueMinimo,

        // #590/RN-NOVA-39 (V0.15.0) — opcional; nulo mantém a regra atual.
        RegraPrecoReferencia regraPrecoReferencia,

        Boolean qualquerMarca,

        // V0.16.0 (#687, RN-NOVA-18) — só para completar insumo em rascunho (INS-003: custo pelo preço e
        // quantidade da compra inicial); ignorados na edição de insumo completo.
        @DecimalMin(value = "0.01", message = "O preço total da compra inicial deve ser maior que zero")
        BigDecimal precoTotalCompraInicial,

        @DecimalMin(value = "0.01", message = "A quantidade comprada inicial deve ser maior que zero")
        BigDecimal quantidadeCompradaInicial
) {
    public InsumoRequestDTO(String nome, String marca, UUID unidadeMedidaId, Boolean fracionavel,
                            TipoExibicaoQuantidade tipoExibicaoQuantidade, Boolean permitirEstoqueNegativo,
                            BigDecimal estoqueAtual, BigDecimal estoqueMinimo) {
        this(nome, marca, unidadeMedidaId, fracionavel, tipoExibicaoQuantidade, permitirEstoqueNegativo,
                estoqueAtual, estoqueMinimo, null);
    }

    public InsumoRequestDTO(String nome, String marca, UUID unidadeMedidaId, Boolean fracionavel,
                            TipoExibicaoQuantidade tipoExibicaoQuantidade, Boolean permitirEstoqueNegativo,
                            BigDecimal estoqueAtual, BigDecimal estoqueMinimo, RegraPrecoReferencia regraPrecoReferencia) {
        this(nome, marca, unidadeMedidaId, fracionavel, tipoExibicaoQuantidade, permitirEstoqueNegativo,
                estoqueAtual, estoqueMinimo, regraPrecoReferencia, null);
    }

    public InsumoRequestDTO(String nome, String marca, UUID unidadeMedidaId, Boolean fracionavel,
                            TipoExibicaoQuantidade tipoExibicaoQuantidade, Boolean permitirEstoqueNegativo,
                            BigDecimal estoqueAtual, BigDecimal estoqueMinimo, RegraPrecoReferencia regraPrecoReferencia,
                            Boolean qualquerMarca) {
        this(nome, marca, unidadeMedidaId, fracionavel, tipoExibicaoQuantidade, permitirEstoqueNegativo,
                estoqueAtual, estoqueMinimo, regraPrecoReferencia, qualquerMarca, null, null);
    }
}
