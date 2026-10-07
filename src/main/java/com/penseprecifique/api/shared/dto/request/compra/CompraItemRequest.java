package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.penseprecifique.api.shared.domain.enums.TipoDesconto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #541 (V0.15.0) — linha da compra. {@code fornecedorId} só é lido no modo múltiplos fornecedores;
 * no modo único, a linha recebe o fornecedor do cabeçalho.
 */
public record CompraItemRequest(
        @NotNull(message = "Escolha o insumo")
        UUID insumoId,

        UUID fornecedorId,

        @Positive(message = "A quantidade deve ser maior que zero")
        @io.swagger.v3.oas.annotations.media.Schema(multipleOf = 0.0001)
        @Digits(integer = 11, fraction = 4, message = "Quantidade com no máximo 4 casas decimais")
        BigDecimal quantidade,

        /** Legado (antes do #576): preço pago da linha. Sem {@code precoCheio}, vale como preço cheio. */
        @Positive(message = "O preço total deve ser maior que zero")
        @io.swagger.v3.oas.annotations.media.Schema(multipleOf = 0.01)
        @Digits(integer = 13, fraction = 2, message = "Preço total com no máximo 2 casas decimais")
        BigDecimal precoTotal,

        /** #576/RN-NOVA-28 — preço cheio da linha, antes do desconto. */
        @Positive(message = "O preço cheio deve ser maior que zero")
        @io.swagger.v3.oas.annotations.media.Schema(multipleOf = 0.01)
        @Digits(integer = 13, fraction = 2, message = "Preço cheio com no máximo 2 casas decimais")
        BigDecimal precoCheio,

        /** Desconto da linha: VALOR (R$) ou PERCENTUAL. Nulo = sem desconto. */
        TipoDesconto descontoTipo,

        @Positive(message = "O desconto deve ser maior que zero")
        @io.swagger.v3.oas.annotations.media.Schema(multipleOf = 0.01)
        @Digits(integer = 13, fraction = 2, message = "Desconto com no máximo 2 casas decimais")
        BigDecimal descontoValor
) {
    /** Forma anterior ao #576 (sem desconto): {@code precoTotal} é o preço cheio e o pago. */
    public CompraItemRequest(UUID insumoId, UUID fornecedorId, BigDecimal quantidade, BigDecimal precoTotal) {
        this(insumoId, fornecedorId, quantidade, precoTotal, null, null, null);
    }

    /** Preço cheio efetivo: o novo campo ou, na forma antiga, o preço total. */
    public BigDecimal precoCheioEfetivo() {
        return precoCheio != null ? precoCheio : precoTotal;
    }
}
