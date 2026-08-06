package com.penseprecifique.api.shared.dto.response.produto;

import lombok.Getter;
import lombok.Setter;

/**
 * Frente 4/P-BE-CONSOLIDADO-001 — badges de filtro de ListaProdutosPage.tsx. Ignora o filtro de
 * `busca` atual da tela (decisão do prompt de origem: badges de categoria são navegação global,
 * não devem mudar conforme o texto digitado) — inclui todos os produtos ativos do usuário
 * (deletedAt IS NULL), independente do que está sendo buscado no momento.
 */
@Getter
@Setter
public class ProdutoContagensResponse {

    private long total;
    private long inativos;
    private PorTipo porTipo;

    @Getter
    @Setter
    public static class PorTipo {
        private long produto;
        private long produtoBase;
        private long customizacao;
    }
}
