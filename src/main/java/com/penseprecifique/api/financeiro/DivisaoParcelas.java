package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * V0.19.0 (RN-NOVA-5 c, d, e, f) — divisão do total em N parcelas iguais, em centavos inteiros: cada uma recebe o total
 * dividido por N arredondado para baixo; a diferença vai para a última (R$ 100,00 em 3x = 33,33 + 33,33 + 33,34). Vence de mês
 * em mês a partir do primeiro vencimento, sempre contado a partir do dia original (31/01, 28/02, 31/03).
 */
public final class DivisaoParcelas {

    public static final int MAXIMO_PARCELAS = 60;

    private DivisaoParcelas() {}

    public static List<NovaParcela> dividir(BigDecimal total, int quantidade, LocalDate primeiroVencimento) {
        if (quantidade < 1 || quantidade > MAXIMO_PARCELAS) {
            throw BusinessException.noCampo("quantidadeParcelas", "O número de parcelas deve ser de 1 a " + MAXIMO_PARCELAS + ".");
        }
        ValidacaoFinanceira.exigirValor(total, "valor");
        ValidacaoFinanceira.exigirVencimento(primeiroVencimento, "vencimento");
        long centavos = total.movePointRight(2).longValueExact();
        if (centavos < quantidade) {
            throw BusinessException.noCampo("quantidadeParcelas",
                    "O valor é pequeno demais para dividir em " + quantidade + " parcelas.");
        }
        long base = centavos / quantidade;
        long resto = centavos - base * quantidade;
        List<NovaParcela> parcelas = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            long c = i == quantidade - 1 ? base + resto : base;
            parcelas.add(new NovaParcela(BigDecimal.valueOf(c, 2), primeiroVencimento.plusMonths(i), null));
        }
        return parcelas;
    }
}
