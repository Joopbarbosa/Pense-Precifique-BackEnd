package com.penseprecifique.api.compra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #959/RN-NOVA-7 (V0.17.0) — entrada de uma compra pendente de recebimento: sempre a compra inteira, com a
 * data do recebimento e a quantidade recebida de cada linha (o preço unitário pago não muda).
 */
public record EntradaCompraRequest(
        @NotNull(message = "Informe a data do recebimento")
        LocalDate dataRecebimento,

        @NotNull(message = "Informe as linhas recebidas")
        @Size(max = 500, message = "Linhas demais")
        @Valid
        List<ItemEntrada> itens
) {
    public record ItemEntrada(
            @NotNull(message = "Informe a linha da compra")
            UUID itemId,

            /** Maior que zero; validado no serviço para listar cada linha com problema na modal de erro. */
            @Digits(integer = 11, fraction = 4, message = "Quantidade com no máximo 4 casas decimais")
            BigDecimal quantidadeRecebida
    ) {}
}
