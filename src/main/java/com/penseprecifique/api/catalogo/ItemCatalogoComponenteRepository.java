package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItemCatalogoComponenteRepository extends JpaRepository<ItemCatalogoComponente, UUID> {

    List<ItemCatalogoComponente> findByItemCatalogoId(UUID itemCatalogoId);

    void deleteByItemCatalogoId(UUID itemCatalogoId);

    /** RN-NOVA-1/DT-NOVA-1 — vínculo de Produto/Customização usado como componente de item de catálogo
     * (substitui ItemCatalogoRepository#findByProdutoIdAndDeletedAtIsNull +
     * ItemCatalogoCustomizacaoRepository#findByProdutoId). */
    @Query("SELECT c FROM ItemCatalogoComponente c " +
            "WHERE c.produtoBase.id = :produtoId AND c.itemCatalogo.deletedAt IS NULL")
    List<ItemCatalogoComponente> findByProdutoBaseId(@Param("produtoId") UUID produtoId);

    /** Mesmo vínculo acima, para Insumo — Insumo nunca pôde ser componente de item de catálogo antes
     * de RN-NOVA-1 (V0.13.0), por isso não existia consulta equivalente até aqui. */
    @Query("SELECT c FROM ItemCatalogoComponente c " +
            "WHERE c.insumo.id = :insumoId AND c.itemCatalogo.deletedAt IS NULL")
    List<ItemCatalogoComponente> findByInsumoId(@Param("insumoId") UUID insumoId);
}
