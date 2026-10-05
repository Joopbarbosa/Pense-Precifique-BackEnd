package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.exception.BusinessException;

/**
 * V0.16.0 (#687, RN-NOVA-18, DT-NOVA-12) — regra única de "insumo utilizável": ativo, não excluído e
 * não rascunho. Vale para ficha técnica, item de catálogo, orçamento, caixa e entrada de estoque.
 * Compra e conciliação de nota aceitam também o rascunho (RN-NOVA-12, RN-NOVA-19).
 */
public final class InsumoUtilizavel {

    static final String TITULO_RASCUNHO = "Insumo em rascunho";
    static final String COMO_RESOLVER_RASCUNHO =
            "Abra o insumo em Insumos, informe unidade, custo e quantidade e salve o cadastro; depois tente de novo.";

    private InsumoUtilizavel() {
    }

    public static boolean utilizavel(Insumo insumo) {
        return Boolean.TRUE.equals(insumo.getAtivo()) && insumo.getDeletedAt() == null && !rascunho(insumo);
    }

    public static boolean rascunho(Insumo insumo) {
        return Boolean.TRUE.equals(insumo.getRascunho());
    }

    /**
     * Bloqueia o uso de insumo em rascunho com a explicação padrão. Inativo continua com a mensagem
     * de cada ponto de uso (quem chama trata antes ou depois, como já fazia).
     *
     * @param ondeUsar complemento da frase, ex.: "adicionado à ficha técnica"
     */
    public static void exigirNaoRascunho(Insumo insumo, String ondeUsar) {
        if (rascunho(insumo)) {
            throw BusinessException.explicado(TITULO_RASCUNHO,
                    "O insumo " + insumo.getNome() + " ainda é um rascunho e não pode ser " + ondeUsar + ".",
                    "Insumo em rascunho ainda não tem unidade e custo revisados; usá-lo agora deixaria o custo errado.",
                    COMO_RESOLVER_RASCUNHO);
        }
    }
}
