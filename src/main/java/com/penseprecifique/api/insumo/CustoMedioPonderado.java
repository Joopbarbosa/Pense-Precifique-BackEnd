package com.penseprecifique.api.insumo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * INS-004 — custo unitário do insumo por média ponderada após uma compra. Extraído de
 * {@code LoteCompraService#registrarCompraIndividual} (removido na V0.15.0) para ser o ponto único
 * do cálculo, chamado pela confirmação da compra (DT-NOVA-3) — nunca duplicar a fórmula.
 *
 * <p>RN-084 (blindagem) — estoque negativo não representa valor de inventário real, só déficit de
 * unidades. Para fins deste cálculo, estoque negativo é tratado como 0: o custo passa a refletir só
 * a compra atual. Sem a blindagem, o valor do estoque anterior ficaria negativo e o denominador
 * (estoque + quantidade) perto de zero, zero ou negativo — custo negativo ou divisão por zero quando
 * a compra compensa exatamente o déficit. A blindagem afeta só a fórmula; a contagem de estoque
 * continua com o valor real.
 *
 * <p>Escala 4, a mesma da coluna {@code insumos.custo_unitario}: o valor calculado é exatamente o
 * que fica gravado, o que permite comparar "custo atual == custo posterior desta compra" no
 * cancelamento (DT-NOVA-4).
 */
public final class CustoMedioPonderado {

    public static final int ESCALA_CUSTO = 4;

    private CustoMedioPonderado() {
    }

    public static BigDecimal calcular(BigDecimal estoqueAnterior, BigDecimal custoAnterior,
                                      BigDecimal quantidadeComprada, BigDecimal precoTotalPago) {
        BigDecimal estoqueParaCalculo = estoqueAnterior.max(BigDecimal.ZERO);
        BigDecimal valorEstoqueAnterior = estoqueParaCalculo.multiply(custoAnterior);
        return valorEstoqueAnterior.add(precoTotalPago)
                .divide(estoqueParaCalculo.add(quantidadeComprada), ESCALA_CUSTO, RoundingMode.HALF_UP);
    }
}
