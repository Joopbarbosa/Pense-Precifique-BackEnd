package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * #543/RN-NOVA-8 (V0.15.0, complementa PDT-003) — modal de impacto da confirmação (ou do
 * cancelamento) de uma compra. Informativo: o preço de venda nunca é alterado pela compra.
 * {@code alterouCustos = false} → nenhum insumo mudou de custo e {@code produtos} vem vazio.
 */
public record ImpactoCompraResponse(
        boolean alterouCustos,
        List<InsumoImpacto> insumos,
        List<ProdutoImpacto> produtos
) {
    public record InsumoImpacto(UUID id, String identificador, String nome, String unidade,
                                BigDecimal custoAntes, BigDecimal custoDepois) {}

    /**
     * {@code direto}: a ficha técnica usa um insumo cujo custo mudou; {@code false} = indireto (usa um
     * produto afetado como componente). {@code precoVenda}/{@code precoVendaManual}: só para exibir.
     */
    public record ProdutoImpacto(UUID id, String identificador, String nome, TipoProduto tipo, boolean direto,
                                 BigDecimal custoAntes, BigDecimal custoDepois,
                                 BigDecimal precoSugeridoAntes, BigDecimal precoSugeridoDepois,
                                 BigDecimal precoVenda, boolean precoVendaManual) {}

    public static ImpactoCompraResponse semAlteracao() {
        return new ImpactoCompraResponse(false, List.of(), List.of());
    }
}
