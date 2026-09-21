package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrcamentoItemRepository extends JpaRepository<OrcamentoItem, UUID> {

    List<OrcamentoItem> findByOrcamentoId(UUID orcamentoId);

    void deleteByOrcamentoId(UUID orcamentoId);

    /** V0.13.0 (#516, RN-NOVA-1) — item de Catálogo deixou de ter "o produto" único (composição de
     * N componentes); agrupa pelo nome do Item de Catálogo nesse caso (todo item, antigo ou novo,
     * tem nome próprio — o backfill de V51 copiou o nome do antigo produto único pros itens já
     * existentes) em vez de tentar atribuir a venda a 1 componente entre N. */
    @Query("""
        SELECT CASE WHEN oi.itemCatalogo IS NOT NULL THEN oi.itemCatalogo.nome ELSE oi.produto.nome END,
               SUM(oi.quantidade)
        FROM OrcamentoItem oi
        WHERE oi.orcamento.usuario.id = :uid
        AND oi.orcamento.deletedAt IS NULL
        GROUP BY CASE WHEN oi.itemCatalogo IS NOT NULL THEN oi.itemCatalogo.nome ELSE oi.produto.nome END
        ORDER BY SUM(oi.quantidade) DESC
    """)
    List<Object[]> findTopProdutosMaisVendidos(@Param("uid") UUID uid, Pageable pageable);
}
