package com.penseprecifique.api.insumo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * #672/RN-NOVA-11, DT-NOVA-7 — linha agregada do histórico do Insumo: "Preço sugerido de N produtos mudou" por evento de custo
 * (a compra COM-N). Abrir a linha lista cada produto com anterior e novo.
 */
public record PrecoSugeridoAgregadoResponse(
        String causa,
        String referenciaTexto,
        int quantidadeProdutos,
        List<ProdutoPrecoSugerido> produtos
) {
    public record ProdutoPrecoSugerido(UUID id, String identificador, String nome, BigDecimal anterior, BigDecimal novo) {}
}
