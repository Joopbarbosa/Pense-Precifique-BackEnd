package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItemCatalogoCustomizacaoRepository extends JpaRepository<ItemCatalogoCustomizacao, UUID> {

    List<ItemCatalogoCustomizacao> findByItemCatalogoId(UUID itemCatalogoId);

    /** PDT-013 — produto referenciado como customização anexada de um item de catálogo não excluído. */
    @Query("SELECT DISTINCT icc.itemCatalogo FROM ItemCatalogoCustomizacao icc " +
            "WHERE icc.produto.id = :produtoId AND icc.itemCatalogo.deletedAt IS NULL")
    List<ItemCatalogo> findItensCatalogoPorProdutoComoCustomizacao(@Param("produtoId") UUID produtoId);

    /** #237/PDT-0XX — linhas de customização anexada que referenciam o produto, para resolução de vínculos. */
    @Query("SELECT icc FROM ItemCatalogoCustomizacao icc " +
            "WHERE icc.produto.id = :produtoId AND icc.itemCatalogo.deletedAt IS NULL")
    List<ItemCatalogoCustomizacao> findByProdutoId(@Param("produtoId") UUID produtoId);
}
