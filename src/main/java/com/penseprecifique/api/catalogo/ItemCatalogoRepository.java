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

    List<ItemCatalogo> findByProdutoIdAndDeletedAtIsNull(UUID produtoId);

    Optional<ItemCatalogo> findByIdAndDeletedAtIsNull(UUID id);

    long countByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);

    /**
     * RN-044/045/046 — busca para a Seção Itens do orçamento: exclui itens de catálogo
     * desativado (RN-046) e itens cujo produto esteja inativo ou excluído (RN-045).
     * P-B008/#353 — {@code Pageable} evita servir a base inteira sem limite conforme o catálogo
     * cresce (mesmo padrão de {@code ProdutoRepository#buscar}); desde RN-NOVA-18 (#353/P-B008), a
     * {@code Page<>} completa é devolvida pelo contrato HTTP (ver {@code
     * ItemCatalogoService#buscarParaOrcamento}), não só o conteúdo.
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
    Page<ItemCatalogo> buscarDisponiveisParaOrcamento(@Param("usuarioId") UUID usuarioId,
                                                        @Param("catalogoId") UUID catalogoId,
                                                        Pageable pageable);

    /**
     * RN-NOVA-6 (#217) — mesma consulta acima com filtro por nome do produto (case-insensitive).
     * Método separado (em vez de `:busca IS NULL OR ...` na mesma query) porque bind de parâmetro
     * nulo dentro de `LOWER(CONCAT(...))` faz o Hibernate/driver inferir o tipo como `bytea` e o
     * Postgres rejeita com "function lower(bytea) does not exist" — mesmo padrão de
     * ProdutoService#listar (`temBusca` decide qual finder chamar), não um `CAST` no JPQL.
     */
    @Query("""
        SELECT ic FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND ic.catalogo.ativo = true
        AND ic.produto.ativo = true
        AND ic.produto.deletedAt IS NULL
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        AND LOWER(ic.produto.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
        ORDER BY ic.produto.nome
    """)
    Page<ItemCatalogo> buscarDisponiveisParaOrcamentoComBusca(@Param("usuarioId") UUID usuarioId,
                                                                 @Param("catalogoId") UUID catalogoId,
                                                                 @Param("busca") String busca,
                                                                 Pageable pageable);
}
