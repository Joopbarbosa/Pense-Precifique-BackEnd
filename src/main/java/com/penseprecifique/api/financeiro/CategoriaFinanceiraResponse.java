package com.penseprecifique.api.financeiro;

import java.util.UUID;

/** V0.19.0 (#1177) — categoria de despesa. {@code semeada}: padrão do sistema (não se renomeia nem se exclui). */
public record CategoriaFinanceiraResponse(UUID id, String nome, boolean semeada, boolean ativa) {

    static CategoriaFinanceiraResponse de(CategoriaFinanceira c) {
        return new CategoriaFinanceiraResponse(c.getId(), c.getNome(), c.isSemeada(), c.isAtiva());
    }
}
