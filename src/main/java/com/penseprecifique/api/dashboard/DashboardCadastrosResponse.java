package com.penseprecifique.api.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #607/RN-NOVA-16 (V0.17.0) — Dashboard de Cadastros: 10 indicadores calculados no backend (vendas pela RN-NOVA-12, compras só as
 * que já deram entrada, RN-NOVA-8). Os rankings trazem o top 5 em {@code top} e todos os itens em {@code todos} (a lupa lista todos).
 */
public record DashboardCadastrosResponse(
        LocalDate de, LocalDate ate, LocalDate deAnterior, LocalDate ateAnterior,
        EvolucaoPreco evolucaoPrecoInsumo,
        Ranking insumosEmProdutos,
        Ranking insumoMaisComprado,
        Ranking insumoMaisGasto,
        Ranking produtoMaisVendido,
        Ranking produtoMaisLucro,
        List<PrecoAbaixoSugerido> produtosComPrecoAbaixoDoSugerido,
        List<MargemProduto> margemPorProduto,
        CurvaAbc curvaAbc,
        Ranking insumosQueMaisVariaram,
        /** V0.17.0 #974 (RN-NOVA-25): faixa de números de estoque e CMV; as listas alimentam as lupas. */
        Estoque estoque
) {
    /** Um item de ranking: {@code valor} é o que ordena (contagem, R$, quantidade ou %); {@code anterior}, o mesmo no período anterior. */
    public record Item(UUID id, String identificador, String rotulo, String marca, BigDecimal valor, BigDecimal anterior, String detalhe) {}

    public record Ranking(List<Item> top, List<Item> todos) {}

    public record PontoPreco(LocalDate mes, BigDecimal precoMedio, BigDecimal quantidade, BigDecimal valorPago) {}

    /** Preço médio pago por mês de um insumo (média ponderada pela quantidade; o mês é o do recebimento). */
    public record EvolucaoPreco(UUID insumoId, String insumo, String marca, List<PontoPreco> pontos) {}

    public record PrecoAbaixoSugerido(UUID id, String identificador, String nome, BigDecimal precoVenda, BigDecimal precoSugerido, BigDecimal diferenca) {}

    public record MargemProduto(UUID id, String identificador, String nome, BigDecimal precoVenda, BigDecimal custo, BigDecimal margemPercentual) {}

    /**
     * Estoque (fotos de hoje) e CMV do período. {@code valorItens}, {@code potencialItens} e {@code abaixoMinimoItens} são as listas das
     * lupas, já ordenadas (maior valor primeiro, depois nome e número).
     */
    public record Estoque(BigDecimal valorTotal, BigDecimal valorMaterial, BigDecimal valorProdutosProntos, List<LinhaEstoque> valorItens,
                          BigDecimal potencialVenda, List<LinhaEstoque> potencialItens,
                          DashboardNumero percentualCmv, DashboardNumero margemMaterial,
                          int insumosAbaixoMinimo, List<LinhaMinimo> abaixoMinimoItens) {}

    /** Insumo ({@code tipo} INSUMO, com custo unitário) ou produto pronto ({@code tipo} PRODUTO, com preço de venda). */
    public record LinhaEstoque(UUID id, String identificador, String nome, String tipo, BigDecimal estoque, BigDecimal custoUnitario,
                               BigDecimal valor, BigDecimal precoVenda) {}

    public record LinhaMinimo(UUID id, String identificador, String nome, BigDecimal estoque, BigDecimal minimo, BigDecimal falta) {}

    public record ItemAbc(UUID id, String identificador, String nome, BigDecimal lucro, BigDecimal percentual, BigDecimal acumulado, String classe) {}

    /** Curva ABC por lucro: só lucro maior que zero entra; zero e prejuízo ficam em {@code semLucro}. */
    public record CurvaAbc(BigDecimal totalLucro, List<ItemAbc> itens, List<ItemAbc> semLucro) {}
}
