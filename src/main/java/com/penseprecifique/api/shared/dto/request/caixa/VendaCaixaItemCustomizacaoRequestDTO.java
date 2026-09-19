package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** #487 — customização ad-hoc anexada a um item da venda (Produto avulso ou ItemCatalogo), além
 *  das fixas do Catálogo (expandidas automaticamente pelo Service). Sem preço — sempre o snapshot
 *  do preço de venda do próprio produto de customização, mesmo padrão de
 *  OrcamentoItemCustomizacaoRequest. */
public record VendaCaixaItemCustomizacaoRequestDTO(
        @NotNull(message = "O produto da customização é obrigatório")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        @Min(value = 1, message = "A quantidade deve ser pelo menos 1")
        Integer quantidade
) {}
