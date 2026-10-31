package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.validation.FaixaDataPermitida;
import com.penseprecifique.api.shared.validation.LimitesTexto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** V0.19.0 (RN-NOVA-5, RN-NOVA-48) — validação única de valor, vencimento e descrição do financeiro; sempre BLOQUEIO por campo, nunca erro interno. */
final class ValidacaoFinanceira {

    static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999.99");
    static final String MENSAGEM_VALOR = "Informe um valor maior que zero.";

    private ValidacaoFinanceira() {}

    /** Maior que zero, no máximo R$ 99.999.999,99 e com no máximo 2 casas decimais. */
    static void exigirValor(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(VALOR_MAXIMO) > 0 || valor.stripTrailingZeros().scale() > 2) {
            throw BusinessException.noCampo(campo, MENSAGEM_VALOR);
        }
    }

    static void exigirVencimento(LocalDate data, String campo) {
        if (!FaixaDataPermitida.contem(data)) {
            throw BusinessException.noCampo(campo, FaixaDataPermitida.MENSAGEM);
        }
    }

    /** Descrição de 1 a 500 caracteres, sem caractere nulo; devolve o texto aparado. */
    static String exigirDescricao(String descricao) {
        String texto = descricao == null ? "" : descricao.strip();
        if (texto.isEmpty()) {
            throw BusinessException.noCampo("descricao", "Informe a descrição.");
        }
        if (texto.length() > LimitesTexto.DESCRICAO_MAX || texto.indexOf('\u0000') >= 0) {
            throw BusinessException.noCampo("descricao", "A descrição deve ter no máximo 500 caracteres e não pode conter caracteres inválidos.");
        }
        return texto;
    }
}
