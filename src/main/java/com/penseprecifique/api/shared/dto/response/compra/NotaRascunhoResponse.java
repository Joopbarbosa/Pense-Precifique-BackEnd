package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * V0.16.0 (#683, DT-NOVA-9) — resultado de {@code POST /compras/nota/rascunho}. Com {@code simular=true}
 * só calcula (linhas, junções, desconto da nota e avisos) e {@code compra} vem nula; sem simular, vem a
 * compra criada (ou o rascunho já existente da mesma nota, RN-NOVA-5).
 */
public record NotaRascunhoResponse(
        CompraResponse compra,
        List<LinhaPrevia> linhas,
        BigDecimal descontoNota,
        BigDecimal acrescimos,
        List<AvisoNota> avisos
) {
    /** Linha da compra que sairia da nota; {@code posicoes} lista os itens da nota somados nela. */
    public record LinhaPrevia(UUID insumoId, String insumoNome, BigDecimal quantidade, BigDecimal precoCheio,
                              BigDecimal descontoLinha, List<Integer> posicoes) {}

    /** AVISO informativo (nunca bloqueia): {@code JUNCAO_DE_ITENS} ou {@code DIFERENCA_NO_TOTAL}. */
    public record AvisoNota(String tipo, String codigo, String mensagem, BigDecimal valor) {}
}
