package com.penseprecifique.api.repository;

import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemCatalogoRepository extends JpaRepository<ItemCatalogo, UUID> {

    List<ItemCatalogo> findByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);

    List<ItemCatalogo> findByProdutoIdAndDeletedAtIsNull(UUID produtoId);

    Optional<ItemCatalogo> findByIdAndDeletedAtIsNull(UUID id);

    long countByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);

    /**
     * RN-044/045/046 — busca para a Seção Itens do orçamento: exclui itens de catálogo
     * desativado (RN-046) e itens cujo produto esteja inativo ou excluído (RN-045).
     */
    @Query("""
        SELECT ic FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND ic.catalogo.ativo = true
        AND ic.produto.ativo = true
        AND ic.produto.deletedAt IS NULL
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        ORDER BY ic.produto.nome
    """)
    List<ItemCatalogo> buscarDisponiveisParaOrcamento(@Param("usuarioId") UUID usuarioId,
                                                       @Param("catalogoId") UUID catalogoId);
}
