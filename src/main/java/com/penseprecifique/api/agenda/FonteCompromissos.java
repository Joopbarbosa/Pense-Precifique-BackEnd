package com.penseprecifique.api.agenda;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * V0.18.0 (DT-NOVA-1) — uma origem de compromissos (Orçamento, Produção, Compra ou da usuária). Os compromissos
 * não são gravados: cada fonte os calcula a cada consulta, a partir do estado atual da origem, e por isso acompanham
 * a origem por construção (RN-NOVA-5 e RN-NOVA-6). Nenhum módulo de origem conhece a Agenda.
 */
public interface FonteCompromissos {

    /**
     * Compromissos da conta com data de origem entre {@code de} e {@code ate} (inclusive) e, além deles, os
     * <b>pendentes</b> com data anterior a {@code hoje} (os atrasados, que a Agenda sobe para hoje). Os de origem
     * automática só voltam enquanto não foram resolvidos; o compromisso da usuária concluído ou cancelado volta no
     * seu dia.
     */
    List<CompromissoBase> listar(UUID usuarioId, LocalDate de, LocalDate ate, LocalDate hoje);

    /** Entra na resposta o que cai no período e o pendente atrasado (a Agenda sobe este para hoje). */
    default boolean relevante(CompromissoBase c, LocalDate de, LocalDate ate, LocalDate hoje) {
        boolean noPeriodo = !c.data().isBefore(de) && !c.data().isAfter(ate);
        return noPeriodo || (c.pendente() && c.data().isBefore(hoje));
    }
}
