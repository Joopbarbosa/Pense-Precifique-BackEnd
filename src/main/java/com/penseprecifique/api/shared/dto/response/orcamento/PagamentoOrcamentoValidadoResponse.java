package com.penseprecifique.api.shared.dto.response.orcamento;

import com.penseprecifique.api.shared.dto.response.compra.MetodoPagamentoRefResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * V0.19.0 (#1185) — resultado da validação do passo Pagamento: o que a #1174 vai gravar. {@code valorSinal} e
 * {@code percentualSinal} nulos sem sinal; {@code saldo} é o total menos o sinal; sinal de 100% não gera parcela de Saldo.
 */
public record PagamentoOrcamentoValidadoResponse(
        MetodoPagamentoRefResponse metodoPagamento,
        boolean temSinal,
        BigDecimal total,
        BigDecimal percentualSinal,
        BigDecimal valorSinal,
        BigDecimal saldo,
        boolean geraParcelaSaldo,
        LocalDate sinalAte,
        LocalDate vencimentoSaldo) {
}
