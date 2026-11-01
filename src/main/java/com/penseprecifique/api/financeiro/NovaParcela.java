package com.penseprecifique.api.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Parcela a criar. {@code baixaInicial} preenchida = a parcela nasce baixada, com origem de baixa AUTOMATICA (DT-NOVA-1).
 * {@code papel} só existe nas parcelas de orçamento (#1174).
 */
public record NovaParcela(BigDecimal valor, LocalDate vencimento, BaixaInicial baixaInicial, PapelParcela papel) {

    public NovaParcela(BigDecimal valor, LocalDate vencimento, BaixaInicial baixaInicial) {
        this(valor, vencimento, baixaInicial, null);
    }

    public record BaixaInicial(LocalDate data, UUID metodoPagamentoId) {}
}
