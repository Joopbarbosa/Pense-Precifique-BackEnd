package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    /**
     * P-F005/#251 (V0.8.1) — substitui os 4 finders derivados por nome que existiam antes
     * (com/sem tipo x com/sem busca) para acomodar uma 3ª dimensão de filtro (semCatalogo, aba
     * "Produto" da venda avulsa) sem explodir a combinatória de nomes. {@code semCatalogo=true}
     * exclui produtos referenciados por um ItemCatalogo não excluído, seja como produto principal
     * do item ou como customização anexada a um item de outro produto (mesmo critério de vínculo
     * usado em {@code ProdutoService#listarCatalogosVinculados}). Sem {@code busca} — ver método
     * abaixo para a variante com busca, separada pelo mesmo motivo já documentado em
     * {@code ItemCatalogoRepository} (bind de parâmetro nulo dentro de LOWER/CONCAT infere tipo
     * incompatível no Postgres).
     */
    @Query("""
        SELECT p FROM Produto p
        WHERE p.usuario.id = :usuarioId
        AND p.deletedAt IS NULL
        AND (:tipo IS NULL OR p.tipo = :tipo)
        AND (:ativo IS NULL OR p.ativo = :ativo)
        AND (:semCatalogo = false OR (
            NOT EXISTS (SELECT 1 FROM ItemCatalogo ic WHERE ic.produto = p AND ic.deletedAt IS NULL)
            AND NOT EXISTS (SELECT 1 FROM ItemCatalogoCustomizacao icc WHERE icc.produto = p AND icc.itemCatalogo.deletedAt IS NULL)
        ))
    """)
    Page<Produto> buscar(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoProduto tipo,
                          @Param("semCatalogo") boolean semCatalogo, @Param("ativo") Boolean ativo, Pageable pageable);

    // #459 (V0.10.0, parent #336) — parâmetro `ativo` novo, mesmo padrão já usado nos outros 3
    // filtros desta query (`IS NULL OR` = sem filtro). Bind direto em `=`, não em LOWER/CONCAT —
    // não sofre do problema de inferência de tipo nulo do Postgres que motivou splitar `busca` em
    // método separado (ver javadoc acima).
    // #487 (V0.12.0) — achado de integração do Frontend do Caixa: UC-NOVO-1 pede busca "por nome
    // ou código de barras" na venda rápida; a busca de produto (usada também por Orçamento/
    // Produção) só cobria nome até esta tarefa. Código de barras é sempre igualdade exata (leitor
    // de código de barras emite o valor completo, nunca um prefixo) — nome continua LIKE parcial.
    @Query("""
        SELECT p FROM Produto p
        WHERE p.usuario.id = :usuarioId
        AND p.deletedAt IS NULL
        AND (:tipo IS NULL OR p.tipo = :tipo)
        AND (:ativo IS NULL OR p.ativo = :ativo)
        AND (:semCatalogo = false OR (
            NOT EXISTS (SELECT 1 FROM ItemCatalogo ic WHERE ic.produto = p AND ic.deletedAt IS NULL)
            AND NOT EXISTS (SELECT 1 FROM ItemCatalogoCustomizacao icc WHERE icc.produto = p AND icc.itemCatalogo.deletedAt IS NULL)
        ))
        AND (LOWER(p.nome) LIKE LOWER(CONCAT('%', :busca, '%')) OR p.codigoBarras = :busca)
    """)
    Page<Produto> buscarComBusca(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoProduto tipo,
                                  @Param("semCatalogo") boolean semCatalogo, @Param("busca") String busca,
                                  @Param("ativo") Boolean ativo, Pageable pageable);

    Optional<Produto> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    List<Produto> findByUsuarioIdAndTipoAndDeletedAtIsNull(UUID usuarioId, TipoProduto tipo);

    Optional<Produto> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    long countByUsuarioIdAndDeletedAtIsNull(UUID usuarioId);

    long countByUsuarioIdAndAtivoFalseAndDeletedAtIsNull(UUID usuarioId);

    /**
     * Frente 4/P-BE-CONSOLIDADO-001 — contagem por categoria (badges de filtro), uma única query
     * GROUP BY em vez de 3 chamadas separadas (uma por TipoProduto).
     */
    @Query("SELECT p.tipo AS tipo, COUNT(p) AS quantidade FROM Produto p " +
            "WHERE p.usuario.id = :usuarioId AND p.deletedAt IS NULL GROUP BY p.tipo")
    List<ContagemPorTipo> contarPorTipo(@Param("usuarioId") UUID usuarioId);

    interface ContagemPorTipo {
        TipoProduto getTipo();
        Long getQuantidade();
    }
}
