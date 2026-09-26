package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** #546 (V0.15.0) — retrato de uma lista gerada (LST-N). Valores copiados na geração. */
public record ListaCompraResponse(
        UUID id,
        Integer numero,
        String identificador,
        LocalDateTime geradaEm,
        List<Item> itens
) {
    public record Item(int ordem, UUID insumoId, String insumoNome, String unidade, BigDecimal estoqueAtual,
                       BigDecimal estoqueMinimo, BigDecimal quantidade, UUID fornecedorId, String fornecedorNome,
                       BigDecimal precoReferencia) {}
}
