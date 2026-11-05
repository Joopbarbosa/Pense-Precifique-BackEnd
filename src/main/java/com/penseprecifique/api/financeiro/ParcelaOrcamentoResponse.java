package com.penseprecifique.api.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** V0.19.0 (#1174) — parcela do lançamento do orçamento, mostrada no detalhe (Sinal, Saldo, Total, Multa). */
public record ParcelaOrcamentoResponse(UUID id, PapelParcela papel, BigDecimal valor, LocalDate vencimento,
                                       SituacaoParcela situacao, LocalDate dataBaixa, String metodoPagamentoNome) {
}
