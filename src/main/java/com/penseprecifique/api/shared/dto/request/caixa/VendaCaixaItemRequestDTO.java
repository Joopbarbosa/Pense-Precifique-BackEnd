package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
        BigDecimal quantidade,

        @Valid
        List<VendaCaixaItemCustomizacaoRequestDTO> customizacoes
) {
    public List<VendaCaixaItemCustomizacaoRequestDTO> customizacoesOuVazio() {
        return customizacoes != null ? customizacoes : List.of();
    }
}
