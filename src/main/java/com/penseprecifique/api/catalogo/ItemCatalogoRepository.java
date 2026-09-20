package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemCatalogoRepository extends JpaRepository<ItemCatalogo, UUID> {

    List<ItemCatalogo> findByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);

    Optional<ItemCatalogo> findByIdAndDeletedAtIsNull(UUID id);

    long countByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);

    /**
     * RN-044/045/046 — busca para a Seção Itens do orçamento: exclui itens de catálogo desativado
     * (RN-046) e itens com qualquer componente inativo/excluído (RN-NOVA-4, V0.13.0 — generaliza
     * RN-045 de "1 produto inativo" para "qualquer componente inativo entre os N"). P-B008/#353 —
     * {@code Pageable} evita servir a base inteira sem limite (mesmo padrão de
     * {@code ProdutoRepository#buscar}); {@code Page<>} completa é devolvida pelo contrato HTTP.
     * Ordena por {@code ic.nome} (V0.13.0 — item de catálogo ganhou nome próprio, RN-NOVA-1; antes
     * ordenava por {@code ic.produto.nome}, que deixou de existir).
     */
    @Query("""
        SELECT ic FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND ic.catalogo.ativo = true
        AND NOT EXISTS (
            SELECT 1 FROM ItemCatalogoComponente comp WHERE comp.itemCatalogo = ic
            AND ((comp.produtoBase IS NOT NULL AND (comp.produtoBase.ativo = false OR comp.produtoBase.deletedAt IS NOT NULL))
              OR (comp.insumo IS NOT NULL AND (comp.insumo.ativo = false OR comp.insumo.deletedAt IS NOT NULL)))
        )
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        ORDER BY ic.nome
    """)
    Page<ItemCatalogo> buscarDisponiveisParaOrcamento(@Param("usuarioId") UUID usuarioId,
                                                        @Param("catalogoId") UUID catalogoId,
                                                        Pageable pageable);

    /**
     * RN-NOVA-6 (#217) — mesma consulta acima com filtro por nome do item (case-insensitive). Método
     * separado (em vez de {@code :busca IS NULL OR ...}) porque bind de parâmetro nulo dentro de
     * {@code LOWER(CONCAT(...))} faz o Postgres inferir o tipo como {@code bytea} e rejeitar — mesmo
     * padrão de {@code ProdutoService#listar}.
     */
    @Query("""
        SELECT ic FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND ic.catalogo.ativo = true
        AND NOT EXISTS (
            SELECT 1 FROM ItemCatalogoComponente comp WHERE comp.itemCatalogo = ic
            AND ((comp.produtoBase IS NOT NULL AND (comp.produtoBase.ativo = false OR comp.produtoBase.deletedAt IS NOT NULL))
              OR (comp.insumo IS NOT NULL AND (comp.insumo.ativo = false OR comp.insumo.deletedAt IS NOT NULL)))
        )
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        AND LOWER(ic.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
        ORDER BY ic.nome
    """)
    Page<ItemCatalogo> buscarDisponiveisParaOrcamentoComBusca(@Param("usuarioId") UUID usuarioId,
                                                                 @Param("catalogoId") UUID catalogoId,
                                                                 @Param("busca") String busca,
                                                                 Pageable pageable);
}
