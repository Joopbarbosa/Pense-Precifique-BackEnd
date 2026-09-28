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
     *
     * <p>#643 — a subconsulta usa LEFT JOIN explícito em produtoBase/insumo: o caminho implícito
     * ({@code comp.produtoBase.ativo}) vira INNER JOIN no Hibernate 6 e, como o componente é XOR, a
     * subconsulta nunca achava linha — item com componente inativo aparecia na busca.
     */
    @Query(value = """
        SELECT ic FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND (:incluirInativos = true OR (ic.catalogo.ativo = true
            AND NOT EXISTS (
                SELECT 1 FROM ItemCatalogoComponente comp LEFT JOIN comp.produtoBase comppb LEFT JOIN comp.insumo compins WHERE comp.itemCatalogo = ic
                AND ((comppb IS NOT NULL AND (comppb.ativo = false OR comppb.deletedAt IS NOT NULL))
                  OR (compins IS NOT NULL AND (compins.ativo = false OR compins.deletedAt IS NOT NULL)))
            )))
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        ORDER BY CASE WHEN ic.catalogo.ativo = true
                AND NOT EXISTS (
                    SELECT 1 FROM ItemCatalogoComponente comp2 LEFT JOIN comp2.produtoBase comp2pb LEFT JOIN comp2.insumo comp2ins WHERE comp2.itemCatalogo = ic
                    AND ((comp2pb IS NOT NULL AND (comp2pb.ativo = false OR comp2pb.deletedAt IS NOT NULL))
                      OR (comp2ins IS NOT NULL AND (comp2ins.ativo = false OR comp2ins.deletedAt IS NOT NULL)))
                ) THEN 0 ELSE 1 END, ic.nome
    """, countQuery = """
        SELECT COUNT(ic) FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND (:incluirInativos = true OR (ic.catalogo.ativo = true
            AND NOT EXISTS (
                SELECT 1 FROM ItemCatalogoComponente comp LEFT JOIN comp.produtoBase comppb LEFT JOIN comp.insumo compins WHERE comp.itemCatalogo = ic
                AND ((comppb IS NOT NULL AND (comppb.ativo = false OR comppb.deletedAt IS NOT NULL))
                  OR (compins IS NOT NULL AND (compins.ativo = false OR compins.deletedAt IS NOT NULL)))
            )))
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
    """)
    Page<ItemCatalogo> buscarDisponiveisParaOrcamento(@Param("usuarioId") UUID usuarioId,
                                                        @Param("catalogoId") UUID catalogoId,
                                                        @Param("incluirInativos") boolean incluirInativos,
                                                        Pageable pageable);

    /**
     * RN-NOVA-6 (#217) — mesma consulta acima com filtro por nome do item (case-insensitive). Método
     * separado (em vez de {@code :busca IS NULL OR ...}) porque bind de parâmetro nulo dentro de
     * {@code LOWER(CONCAT(...))} faz o Postgres inferir o tipo como {@code bytea} e rejeitar — mesmo
     * padrão de {@code ProdutoService#listar}.
     *
     * <p>#641/RN-NOVA-40 (V0.15.0) — {@code incluirInativos = true} traz também os itens indisponíveis
     * (catálogo inativo ou componente inativo/excluído), sempre depois dos disponíveis; o seletor os
     * mostra riscados, sem poder escolher. Vale para as duas queries.
     */
    @Query(value = """
        SELECT ic FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND (:incluirInativos = true OR (ic.catalogo.ativo = true
            AND NOT EXISTS (
                SELECT 1 FROM ItemCatalogoComponente comp LEFT JOIN comp.produtoBase comppb LEFT JOIN comp.insumo compins WHERE comp.itemCatalogo = ic
                AND ((comppb IS NOT NULL AND (comppb.ativo = false OR comppb.deletedAt IS NOT NULL))
                  OR (compins IS NOT NULL AND (compins.ativo = false OR compins.deletedAt IS NOT NULL)))
            )))
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        AND LOWER(ic.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
        ORDER BY CASE WHEN ic.catalogo.ativo = true
                AND NOT EXISTS (
                    SELECT 1 FROM ItemCatalogoComponente comp2 LEFT JOIN comp2.produtoBase comp2pb LEFT JOIN comp2.insumo comp2ins WHERE comp2.itemCatalogo = ic
                    AND ((comp2pb IS NOT NULL AND (comp2pb.ativo = false OR comp2pb.deletedAt IS NOT NULL))
                      OR (comp2ins IS NOT NULL AND (comp2ins.ativo = false OR comp2ins.deletedAt IS NOT NULL)))
                ) THEN 0 ELSE 1 END, ic.nome
    """, countQuery = """
        SELECT COUNT(ic) FROM ItemCatalogo ic
        WHERE ic.catalogo.usuario.id = :usuarioId
        AND ic.deletedAt IS NULL
        AND (:incluirInativos = true OR (ic.catalogo.ativo = true
            AND NOT EXISTS (
                SELECT 1 FROM ItemCatalogoComponente comp LEFT JOIN comp.produtoBase comppb LEFT JOIN comp.insumo compins WHERE comp.itemCatalogo = ic
                AND ((comppb IS NOT NULL AND (comppb.ativo = false OR comppb.deletedAt IS NOT NULL))
                  OR (compins IS NOT NULL AND (compins.ativo = false OR compins.deletedAt IS NOT NULL)))
            )))
        AND (:catalogoId IS NULL OR ic.catalogo.id = :catalogoId)
        AND LOWER(ic.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
    """)
    Page<ItemCatalogo> buscarDisponiveisParaOrcamentoComBusca(@Param("usuarioId") UUID usuarioId,
                                                                 @Param("catalogoId") UUID catalogoId,
                                                                 @Param("busca") String busca,
                                                                 @Param("incluirInativos") boolean incluirInativos,
                                                                 Pageable pageable);
}
