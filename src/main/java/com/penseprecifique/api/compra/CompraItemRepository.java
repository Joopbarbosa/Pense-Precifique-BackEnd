package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CompraItemRepository extends JpaRepository<CompraItem, UUID> {

    @EntityGraph(attributePaths = {"insumo", "insumo.unidadeMedida", "fornecedor"})
    List<CompraItem> findByCompraIdOrderByOrdemAsc(UUID compraId);

    @EntityGraph(attributePaths = {"insumo", "insumo.unidadeMedida", "fornecedor", "compra"})
    List<CompraItem> findByCompraIdIn(Collection<UUID> compraIds);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM CompraItem ci WHERE ci.compra.id = :compraId")
    void deleteByCompraId(@Param("compraId") UUID compraId);

    /**
     * Linhas de compras em um status, de um usuário, para um conjunto de insumos — base de "última
     * compra do insumo" (RN-NOVA-9), do fornecedor da compra mais recente (RN-NOVA-12) e dos gráficos.
     */
    @EntityGraph(attributePaths = {"compra", "fornecedor", "insumo"})
    @Query("SELECT ci FROM CompraItem ci WHERE ci.compra.usuario.id = :usuarioId " +
            "AND ci.compra.deletedAt IS NULL AND ci.compra.status = :status AND ci.insumo.id IN :insumoIds")
    List<CompraItem> findPorInsumosEStatus(@Param("usuarioId") UUID usuarioId,
                                           @Param("insumoIds") Collection<UUID> insumoIds,
                                           @Param("status") StatusCompra status);

    /** Todas as linhas de compras em um status, de um usuário (dashboard, indicadores de fornecedor). */
    @EntityGraph(attributePaths = {"compra", "fornecedor", "insumo", "insumo.unidadeMedida"})
    @Query("SELECT ci FROM CompraItem ci WHERE ci.compra.usuario.id = :usuarioId " +
            "AND ci.compra.deletedAt IS NULL AND ci.compra.status = :status")
    List<CompraItem> findPorStatus(@Param("usuarioId") UUID usuarioId, @Param("status") StatusCompra status);
}
