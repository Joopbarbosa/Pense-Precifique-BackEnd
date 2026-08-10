package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FichaTecnicaItemRepository extends JpaRepository<FichaTecnicaItem, UUID> {

    List<FichaTecnicaItem> findByProdutoId(UUID produtoId);

    void deleteByProdutoId(UUID produtoId);

    @Query("SELECT DISTINCT f.produto FROM FichaTecnicaItem f WHERE f.insumo.id = :insumoId AND f.produto.deletedAt IS NULL")
    List<Produto> findProdutosByInsumoId(@Param("insumoId") UUID insumoId);

    List<FichaTecnicaItem> findByProdutoIdAndInsumoId(UUID produtoId, UUID insumoId);

    /** #237/PDT-0XX — produtos (pai) cuja ficha técnica usa o produto informado como componente (produtoBase). */
    @Query("SELECT DISTINCT f.produto FROM FichaTecnicaItem f WHERE f.produtoBase.id = :produtoBaseId AND f.produto.deletedAt IS NULL")
    List<Produto> findProdutosByProdutoBaseId(@Param("produtoBaseId") UUID produtoBaseId);

    List<FichaTecnicaItem> findByProdutoBaseId(UUID produtoBaseId);
}
