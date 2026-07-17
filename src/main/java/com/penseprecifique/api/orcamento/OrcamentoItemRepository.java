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

    @Query("""
        SELECT oi.itemCatalogo.produto.nome, SUM(oi.quantidade)
        FROM OrcamentoItem oi
        WHERE oi.orcamento.usuario.id = :uid
        AND oi.orcamento.deletedAt IS NULL
        GROUP BY oi.itemCatalogo.produto.nome
        ORDER BY SUM(oi.quantidade) DESC
    """)
    List<Object[]> findTopProdutosMaisVendidos(@Param("uid") UUID uid, Pageable pageable);
}
