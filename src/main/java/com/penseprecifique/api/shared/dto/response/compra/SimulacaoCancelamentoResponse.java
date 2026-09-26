package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * #544/RN-NOVA-9 (V0.15.0) — prévia do cancelamento (padrão simular-*): nada é gravado.
 * {@code bloqueios} não vazio → o cancelamento inteiro é BLOQUEADO (estoque ficaria negativo em
 * insumo que não permite, INS-009). {@code avisos} → AVISO: esses insumos manterão o custo atual
 * (houve outra mudança de custo depois desta compra); cancelar exige {@code confirmarManterCusto}.
 */
public record SimulacaoCancelamentoResponse(
        boolean podeCancelar,
        List<EstoqueNegativo> bloqueios,
        List<CustoMantido> avisos
) {
    public record EstoqueNegativo(UUID insumoId, String nome, String unidade, BigDecimal estoqueAtual,
                                  BigDecimal quantidadeEstornada, BigDecimal estoqueResultante) {}

    public record CustoMantido(UUID insumoId, String nome, BigDecimal custoAtual, BigDecimal custoAntesDaCompra) {}
}
