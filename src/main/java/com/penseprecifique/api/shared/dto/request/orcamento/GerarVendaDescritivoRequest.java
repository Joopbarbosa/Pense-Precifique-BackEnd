package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * #382 (RN-NOVA-44, CEN-NOVO-49/57) — opções que o cliente escolheu (com quantidade) e, se o Descritivo não tem cliente,
 * o cliente escolhido na hora (não altera o Descritivo). A lista é validada no serviço (1 a 100, sem repetição).
 */
public record GerarVendaDescritivoRequest(
        UUID clienteId,
        @Valid List<Escolha> itens) {

    public record Escolha(
            @NotNull(message = "Informe a opção.") UUID opcaoId,
            @NotNull(message = "Informe a quantidade.")
            @Min(value = 1, message = "A quantidade deve ser de pelo menos 1.")
            @Max(value = 100000, message = "Quantidade acima do máximo permitido.")
            Integer quantidade) {
    }
}
