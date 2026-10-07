package com.penseprecifique.api.shared.dto.request.compra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * #546/RN-NOVA-12 (V0.15.0) — "Gerar" a lista a partir da prévia revisada pela artesã (quantidade e
 * fornecedor editáveis por linha). Quantidade > 0 é validada no Service, com mensagem por linha.
 */
public record GerarListaCompraRequest(
        @Valid List<Linha> itens
) {
    public record Linha(
            @NotNull(message = "Escolha o insumo")
            UUID insumoId,

            @Digits(integer = 11, fraction = 4, message = "Quantidade com no máximo 4 casas decimais")
            BigDecimal quantidade,

            UUID fornecedorId
    ) {}

    public List<Linha> itensOuVazio() {
        return itens != null ? itens : List.of();
    }
}
