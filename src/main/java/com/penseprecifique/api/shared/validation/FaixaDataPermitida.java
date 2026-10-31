package com.penseprecifique.api.shared.validation;

import java.time.LocalDate;

/** Faixa de datas aceita pelas consultas e operações do backend. */
public final class FaixaDataPermitida {

    public static final String MENSAGEM = "As datas precisam estar entre 01/01/2000 e 31/12/2100.";

    private static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate DATA_MAXIMA = LocalDate.of(2100, 12, 31);

    private FaixaDataPermitida() {}

    public static boolean contem(LocalDate data) {
        return data != null && !data.isBefore(DATA_MINIMA) && !data.isAfter(DATA_MAXIMA);
    }
}
