package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Produto;

import java.util.UUID;

/**
 * #720/RN-NOVA-14, DT-NOVA-12 (V0.17.0) — id do cadastro que o item de um registro (orçamento, venda do Caixa, compra) abre em
 * aba nova: Produto, Insumo ou Catálogo (o item de Catálogo não tem página própria, então abre o Catálogo a que pertence).
 * Quem decide se o cadastro ainda existe é o backend (exclusão lógica): excluído devolve {@code null} e o front não desenha link.
 * Cadastro inativo continua abrindo.
 */
public final class DestinoCadastro {

    private DestinoCadastro() {
    }

    public static UUID produto(Produto produto) {
        return produto != null && produto.getDeletedAt() == null ? produto.getId() : null;
    }

    public static UUID insumo(Insumo insumo) {
        return insumo != null && insumo.getDeletedAt() == null ? insumo.getId() : null;
    }

    /** Id do Catálogo dono do item; nulo se o item foi excluído ou não tem Catálogo. */
    public static UUID catalogoDoItem(ItemCatalogo item) {
        return item != null && item.getDeletedAt() == null && item.getCatalogo() != null ? item.getCatalogo().getId() : null;
    }
}
