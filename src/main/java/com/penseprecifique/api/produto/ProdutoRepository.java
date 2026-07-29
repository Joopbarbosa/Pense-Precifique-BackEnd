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

    Page<Produto> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Produto> findByUsuarioIdAndTipoAndDeletedAtIsNull(UUID usuarioId, TipoProduto tipo, Pageable pageable);

    Page<Produto> findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, String nome, Pageable pageable);

    Page<Produto> findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, TipoProduto tipo, String nome, Pageable pageable);

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
