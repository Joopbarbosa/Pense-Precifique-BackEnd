package com.penseprecifique.api.orcamento.descritivo;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** #382 (DT-NOVA-10) — consultas do orçamento Descritivo; as da Venda ficam no {@code OrcamentoRepository}. */
public interface OrcamentoDescritivoRepository extends JpaRepository<Orcamento, UUID> {

    @Query("""
            SELECT o FROM Orcamento o
            WHERE o.id = :id AND o.usuario.id = :usuarioId AND o.deletedAt IS NULL
            AND o.tipo = com.penseprecifique.api.shared.domain.enums.TipoOrcamento.DESCRITIVO
            """)
    Optional<Orcamento> buscarDescritivo(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    @Query("""
            SELECT COUNT(o) > 0 FROM Orcamento o
            WHERE o.id = :id AND o.usuario.id = :usuarioId AND o.deletedAt IS NULL
            AND o.tipo = com.penseprecifique.api.shared.domain.enums.TipoOrcamento.DESCRITIVO
            """)
    boolean existeDescritivo(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    @Query(value = """
            SELECT o FROM Orcamento o LEFT JOIN o.cliente c
            WHERE o.usuario.id = :usuarioId AND o.deletedAt IS NULL
            AND o.tipo = com.penseprecifique.api.shared.domain.enums.TipoOrcamento.DESCRITIVO
            AND (:status IS NULL OR o.status = :status)
            AND (CAST(:busca AS string) IS NULL
                 OR LOWER(o.titulo) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%'))
                 OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
            ORDER BY o.numero DESC
            """,
            countQuery = """
            SELECT COUNT(o) FROM Orcamento o LEFT JOIN o.cliente c
            WHERE o.usuario.id = :usuarioId AND o.deletedAt IS NULL
            AND o.tipo = com.penseprecifique.api.shared.domain.enums.TipoOrcamento.DESCRITIVO
            AND (:status IS NULL OR o.status = :status)
            AND (CAST(:busca AS string) IS NULL
                 OR LOWER(o.titulo) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%'))
                 OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
            """)
    Page<Orcamento> buscar(@Param("usuarioId") UUID usuarioId, @Param("status") StatusOrcamento status,
                           @Param("busca") String busca, Pageable pageable);

    /** Maior número já usado pela conta (Venda e Descritivo dividem o ORC-N, RN-053). */
    @Query("SELECT MAX(o.numero) FROM Orcamento o WHERE o.usuario.id = :usuarioId")
    Integer findTopNumero(@Param("usuarioId") UUID usuarioId);
}
