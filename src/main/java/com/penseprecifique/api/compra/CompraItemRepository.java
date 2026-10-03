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

    /**
     * #590/RN-NOVA-39 — linhas de compras CONFIRMADAS de um par fornecedor + insumo com data da compra
     * a partir de {@code desde} (janela de 12 meses do preço de referência).
     */
    @Query("SELECT ci FROM CompraItem ci WHERE ci.fornecedor.id = :fornecedorId AND ci.insumo.id = :insumoId " +
            "AND ci.compra.deletedAt IS NULL AND ci.compra.status = com.penseprecifique.api.shared.domain.enums.StatusCompra.CONFIRMADA " +
            "AND ci.compra.dataCompra >= :desde")
    List<CompraItem> findConfirmadasDoPar(@Param("fornecedorId") UUID fornecedorId,
                                          @Param("insumoId") UUID insumoId,
                                          @Param("desde") java.time.LocalDate desde);

    /** #586/#590 — linhas CONFIRMADAS do par, mais recente primeiro (use com PageRequest.of(0, 1)). */
    @EntityGraph(attributePaths = {"compra"})
    @Query("SELECT ci FROM CompraItem ci WHERE ci.fornecedor.id = :fornecedorId AND ci.insumo.id = :insumoId " +
            "AND ci.compra.deletedAt IS NULL AND ci.compra.status = com.penseprecifique.api.shared.domain.enums.StatusCompra.CONFIRMADA " +
            "ORDER BY ci.compra.dataCompra DESC, ci.compra.numero DESC")
    List<CompraItem> findUltimasConfirmadasDoPar(@Param("fornecedorId") UUID fornecedorId,
                                                 @Param("insumoId") UUID insumoId,
                                                 org.springframework.data.domain.Pageable pageable);

    /** #596/RN-NOVA-41 — ids dos insumos em compras CONFIRMADAS criadas a partir da lista. */
    @Query("SELECT DISTINCT ci.insumo.id FROM CompraItem ci WHERE ci.compra.listaCompra.id = :listaId " +
            "AND ci.compra.deletedAt IS NULL AND ci.compra.status = com.penseprecifique.api.shared.domain.enums.StatusCompra.CONFIRMADA")
    List<UUID> findInsumosCompradosDaLista(@Param("listaId") UUID listaId);

    /** Todas as linhas de compras em um status, de um usuário (dashboard, indicadores de fornecedor). */
    @EntityGraph(attributePaths = {"compra", "fornecedor", "insumo", "insumo.unidadeMedida"})
    @Query("SELECT ci FROM CompraItem ci WHERE ci.compra.usuario.id = :usuarioId " +
            "AND ci.compra.deletedAt IS NULL AND ci.compra.status = :status")
    List<CompraItem> findPorStatus(@Param("usuarioId") UUID usuarioId, @Param("status") StatusCompra status);
}
