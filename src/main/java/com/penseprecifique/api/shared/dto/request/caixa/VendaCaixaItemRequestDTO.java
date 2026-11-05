package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Origem XOR (reabertura de RN-NOVA-1, V0.12.0, achado do teste manual): {@code itemCatalogoId}
 * (Catálogo, com customizações fixas expandidas automaticamente) OU {@code produtoId} (produto
 * direto) — validado no {@code VendaCaixaService}, mesma trava condicional de
 * {@code OrcamentoItemRequest}. {@code customizacoes} funciona para as duas origens.
 */
public record VendaCaixaItemRequestDTO(
        UUID itemCatalogoId,

        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade deve ser no mínimo 0,01")
        @DecimalMax(value = "999999.99", message = "A quantidade deve ser no máximo 999.999,99")
        @Digits(integer = 6, fraction = 2, message = "A quantidade deve ter no máximo duas casas decimais")
        BigDecimal quantidade,

        @Valid
        List<VendaCaixaItemCustomizacaoRequestDTO> customizacoes
) {
    public List<VendaCaixaItemCustomizacaoRequestDTO> customizacoesOuVazio() {
        return customizacoes != null ? customizacoes : List.of();
    }
}
