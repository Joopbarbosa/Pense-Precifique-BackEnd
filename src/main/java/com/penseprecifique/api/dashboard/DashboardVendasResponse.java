package com.penseprecifique.api.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #719/RN-NOVA-17 (V0.17.0) — aba Gráficos do Dashboard de Vendas. Tudo calculado no backend sobre as vendas da RN-NOVA-12
 * (fonte única {@link VendasService}); o front só desenha.
 */
public record DashboardVendasResponse(
        LocalDate de, LocalDate ate, LocalDate deAnterior, LocalDate ateAnterior,
        DashboardNumero faturamento,
        DashboardNumero quantidadeVendas,
        DashboardNumero ticketMedio,
        List<Mes> meses,
        List<Origem> porOrigem,
        List<ProdutoVendido> produtosMaisVendidos,
        /** V0.17.0 #971 (DT-NOVA-18): todos os produtos vendidos no período, para a lupa do ranking. */
        List<ProdutoVendido> todosProdutosVendidos,
        List<FormaPagamento> formasPagamento
) {
    public record Mes(LocalDate mes, BigDecimal faturamento, long quantidade) {}

    /** {@code origem}: VENDA_CAIXA ou ORCAMENTO. */
    public record Origem(String origem, BigDecimal faturamento, long quantidade) {}

    /** Produto (ou item de Catálogo) vendido no período: quantidade e faturamento das linhas. */
    public record ProdutoVendido(UUID id, String tipo, String nome, BigDecimal quantidade, BigDecimal faturamento) {}

    /** Valor por forma de pagamento registrada na venda; "Não informada" quando a venda não tem forma. */
    public record FormaPagamento(String forma, BigDecimal valor, long quantidade) {}
}
