package com.penseprecifique.api.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** #719/#607 (DT-NOVA-10) — um número do painel com o do período anterior e a variação percentual (calculada aqui, nunca no front). */
public record DashboardNumero(BigDecimal valor, BigDecimal anterior, BigDecimal variacaoPercentual) {

    private static final BigDecimal CEM = new BigDecimal("100");

    public static DashboardNumero de(BigDecimal atual, BigDecimal anterior) {
        return new DashboardNumero(atual, anterior, variacao(anterior, atual));
    }

    /** Variação de {@code base} para {@code atual}, 2 casas; nula sem base ou com base zero. */
    public static BigDecimal variacao(BigDecimal base, BigDecimal atual) {
        if (base == null || atual == null || base.signum() == 0) {
            return null;
        }
        return atual.subtract(base).multiply(CEM).divide(base, 2, RoundingMode.HALF_UP);
    }
}
