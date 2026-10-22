package com.penseprecifique.api.shared.dto.response.agenda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * V0.18.0 (#1071, RN-NOVA-27) — o Resumo do dia: o que é de hoje e o que está atrasado, por assunto. Tudo calculado na consulta,
 * sem IA. "A pagar" já tem o seu lugar e fica indisponível até o módulo Financeiro existir.
 */
public record ResumoDoDiaResponse(
        LocalDate data,
        long paraHoje,
        long atrasados,
        Valores aReceber,
        Valores aPagar,
        List<CompromissoResponse> entregas,
        List<CompromissoResponse> producoes,
        List<CompromissoResponse> outros,
        List<InsumoFaltante> insumosFaltantes) {

    /** Bloco com valor em reais: `disponivel` falso quando a fonte ainda não existe (a pagar); totais nulos nesse caso. */
    public record Valores(boolean disponivel, BigDecimal total, BigDecimal totalAtrasado, List<CompromissoResponse> itens) {}

    /** Insumo cujo estoque não cobre as produções do dia/atrasadas que ainda não começaram. */
    public record InsumoFaltante(String nome, String siglaUnidade, BigDecimal necessario, BigDecimal emEstoque, BigDecimal falta) {}
}
