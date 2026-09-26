package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * #548/RN-NOVA-15 (V0.15.0) — cards do dashboard de compras. Só compras CONFIRMADAS. Campos nulos =
 * sem dado ({@code insumoMaiorAumento} nulo → "Sem dados suficientes").
 */
public record DashboardComprasResponse(
        BigDecimal totalGastoMes,
        BigDecimal totalGastoAno,
        FornecedorMaisUsado fornecedorMaisUsado,
        InsumoMaiorAumento insumoMaiorAumento
) {
    public record FornecedorMaisUsado(CadastroRefResponse fornecedor, long quantidadeCompras) {}

    public record InsumoMaiorAumento(InsumoRefResponse insumo, BigDecimal precoInicial, LocalDate dataInicial,
                                     BigDecimal precoFinal, LocalDate dataFinal, BigDecimal variacaoPercentual) {}
}
