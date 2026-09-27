package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * #576/RN-NOVA-28 (V0.15.0, DT-NOVA-17) — desconto de linha e de nota. Recalcula, a partir do preço
 * cheio e do que foi digitado, o desconto de cada linha em R$, o desconto da nota em R$ e o rateio
 * dele, e grava o preço PAGO em {@code precoTotal} (o que custo médio, vínculo, lista e evolução leem).
 *
 * <ol>
 *   <li>Desconto da linha = valor digitado, ou preço cheio × % ÷ 100.</li>
 *   <li>Valor da linha = preço cheio − desconto da linha.</li>
 *   <li>Desconto da nota = valor digitado, ou soma dos valores das linhas × % ÷ 100.</li>
 *   <li>Rateio proporcional ao valor da linha; a sobra de centavos vai para a linha de maior valor
 *       (empate: a primeira).</li>
 *   <li>Preço pago = valor da linha − parte da nota.</li>
 * </ol>
 * Linha sem preço cheio (rascunho) fica sem preço pago e fora do rateio; na confirmação todas têm preço.
 */
final class DescontoCompra {

    private static final BigDecimal CEM = new BigDecimal("100");

    private DescontoCompra() {
    }

    static void aplicar(Compra compra, List<CompraItem> itens) {
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            CompraItem item = itens.get(i);
            item.setDescontoNota(BigDecimal.ZERO);
            if (item.getPrecoCheio() == null) {
                item.setDescontoLinha(BigDecimal.ZERO);
                item.setPrecoTotal(null);
                continue;
            }
            BigDecimal desconto = valorDoDesconto(item.getDescontoTipo(), item.getDescontoInformado(), item.getPrecoCheio(),
                    "Linha " + (i + 1) + " (" + item.getInsumo().getNome() + ")");
            if (desconto.compareTo(item.getPrecoCheio()) >= 0) {
                throw new BusinessException("Linha " + (i + 1) + " (" + item.getInsumo().getNome()
                        + "): o desconto precisa ser menor que o preço cheio.");
            }
            item.setDescontoLinha(desconto);
            item.setPrecoTotal(item.getPrecoCheio().subtract(desconto));
            soma = soma.add(item.getPrecoTotal());
        }

        BigDecimal descontoNota = valorDoDesconto(compra.getDescontoNotaTipo(), compra.getDescontoNotaInformado(), soma,
                "Desconto da nota");
        compra.setDescontoNota(descontoNota);
        if (descontoNota.signum() == 0 || soma.signum() == 0) {
            return;
        }
        if (descontoNota.compareTo(soma) >= 0) {
            throw new BusinessException("O desconto da nota precisa ser menor que a soma das linhas.");
        }

        CompraItem maior = null;
        BigDecimal rateado = BigDecimal.ZERO;
        for (CompraItem item : itens) {
            if (item.getPrecoTotal() == null) {
                continue;
            }
            if (maior == null || item.getPrecoTotal().compareTo(maior.getPrecoTotal()) > 0) {
                maior = item;
            }
            BigDecimal parte = descontoNota.multiply(item.getPrecoTotal()).divide(soma, 2, RoundingMode.HALF_UP);
            item.setDescontoNota(parte);
            rateado = rateado.add(parte);
        }
        maior.setDescontoNota(maior.getDescontoNota().add(descontoNota.subtract(rateado)));

        for (int i = 0; i < itens.size(); i++) {
            CompraItem item = itens.get(i);
            if (item.getPrecoTotal() == null) {
                continue;
            }
            item.setPrecoTotal(item.getPrecoTotal().subtract(item.getDescontoNota()));
            if (item.getPrecoTotal().signum() <= 0) {
                throw new BusinessException("Linha " + (i + 1) + " (" + item.getInsumo().getNome()
                        + "): o preço pago ficou zerado com os descontos.");
            }
        }
    }

    private static BigDecimal valorDoDesconto(TipoDesconto tipo, BigDecimal informado, BigDecimal base, String onde) {
        if (tipo == null || informado == null) {
            return BigDecimal.ZERO;
        }
        if (tipo == TipoDesconto.VALOR) {
            return informado.setScale(2, RoundingMode.HALF_UP);
        }
        if (informado.signum() <= 0 || informado.compareTo(CEM) >= 0) {
            throw new BusinessException(onde + ": o desconto em % precisa ser maior que 0 e menor que 100.");
        }
        return base.multiply(informado).divide(CEM, 2, RoundingMode.HALF_UP);
    }
}
