package com.penseprecifique.api.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * V0.19.0 (RN-NOVA-4) — uma linha das listas A pagar / A receber. {@code situacao} é a gravada; {@code situacaoExibida} é
 * derivada pelo relógio de Brasília (DT-NOVA-2): EM_ABERTO, VENCE_HOJE, VENCIDA, PAGA, RECEBIDA ou CANCELADA.
 * {@code rotulo} traz o texto pronto ("Vencida há 5 dias", "Vence hoje", "Paga", "Recebida"...).
 */
public record ParcelaFinanceiraResponse(
        UUID id, UUID lancamentoId, TipoLancamento tipo, String descricao,
        OrigemLancamento origem, UUID compraId, UUID orcamentoId,
        int numero, int totalParcelas, BigDecimal valor, LocalDate vencimento,
        SituacaoParcela situacao, String situacaoExibida, Long diasEmAtraso, String rotulo,
        LocalDate dataBaixa, UUID metodoPagamentoId, String metodoPagamentoNome, OrigemBaixa origemBaixa) {
}
