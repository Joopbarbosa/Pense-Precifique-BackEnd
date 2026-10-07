package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.dto.response.cliente.QuantidadeValorResponse;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * #577/RN-NOVA-29 (V0.15.0, DT-NOVA-18) — painel da aba Dashboard de Minhas compras, para o período
 * {@code de}–{@code ate}, com comparação ao período anterior ({@code deAnterior}–{@code ateAnterior}).
 * Só compras CONFIRMADAS. Substitui o shape da RN-NOVA-15 (cards de mês/ano/fornecedor mais usado).
 *
 * <p>{@code variacaoPercentual} de cada número: (atual − anterior) ÷ anterior × 100, 2 casas; nulo
 * sem base anterior. {@code meses}: do início do período (ou 6 meses antes do fim, o que vier antes)
 * até o mês do fim.
 */
public record DashboardComprasResponse(
        LocalDate de,
        LocalDate ate,
        LocalDate deAnterior,
        LocalDate ateAnterior,
        Numero gasto,
        Numero quantidadeCompras,
        Numero ticketMedio,
        Economia economia,
        Cmv cmv,
        QuantidadeValorResponse naoPagas,
        @Schema(nullable = true) InsumoVariacao maiorAumento,
        List<Mes> meses,
        List<InsumoVariacao> insumosQueMaisSubiram,
        List<FornecedorGasto> fornecedoresPorGasto,
        List<FornecedorDesconto> fornecedoresPorDescontoPercentual,
        List<FornecedorDesconto> fornecedoresPorDescontoValor
) {
    public record Numero(@Schema(nullable = true) BigDecimal valor, @Schema(nullable = true) BigDecimal anterior, @Schema(nullable = true) BigDecimal variacaoPercentual) {}

    /** Descontos (linha + parte da nota) e % sobre a soma dos preços cheios. */
    public record Economia(BigDecimal valor, BigDecimal totalCheio, @Schema(nullable = true) BigDecimal percentual,
                           @Schema(nullable = true) BigDecimal anterior, @Schema(nullable = true) BigDecimal variacaoPercentual) {}

    /**
     * RN-NOVA-27 — CMV (R$) e CMV % sobre o faturamento; {@code estimado} = parte do CMV calculada com o
     * custo de hoje; {@code vendasSemCusto} = vendas com alguma linha fora do cálculo.
     */
    public record Cmv(BigDecimal valor, BigDecimal faturamento, @Schema(nullable = true) BigDecimal percentual, BigDecimal estimado,
                      long vendasSemCusto, @Schema(nullable = true) BigDecimal anterior, @Schema(nullable = true) BigDecimal percentualAnterior,
                      @Schema(nullable = true) BigDecimal variacaoPercentual) {}

    /** {@code mes} = 1º dia do mês. CMV % nulo sem faturamento. */
    public record Mes(LocalDate mes, BigDecimal gasto, BigDecimal cmv, BigDecimal faturamento, @Schema(nullable = true) BigDecimal cmvPercentual) {}

    public record InsumoVariacao(InsumoRefResponse insumo, BigDecimal precoInicial, LocalDate dataInicial,
                                 BigDecimal precoFinal, LocalDate dataFinal, @Schema(nullable = true) BigDecimal variacaoPercentual) {}

    public record FornecedorGasto(CadastroRefResponse fornecedor, BigDecimal valor, long quantidadeCompras) {}

    public record FornecedorDesconto(CadastroRefResponse fornecedor, BigDecimal desconto, BigDecimal totalCheio,
                                     BigDecimal percentual) {}
}
