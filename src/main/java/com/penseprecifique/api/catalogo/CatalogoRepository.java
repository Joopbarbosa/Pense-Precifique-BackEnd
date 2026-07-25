package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogoRepository extends JpaRepository<Catalogo, UUID> {

    Optional<Catalogo> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    Optional<Catalogo> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Catalogo> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    boolean existsByUsuarioIdAndNomeIgnoreCase(UUID usuarioId, String nome);

    /**
     * #133/RN-057 — substitui a ordenação em memória por Pageable server-side (mesmo padrão de
     * ProducaoRepository.buscarIdsOrdenados, #158). Retorna só os IDs, ordenados conforme o Sort do
     * Pageable (allowlist validada e resolvida em CatalogoService.resolverPageableOrdenado). GROUP BY
     * c.id (em vez de DISTINCT) porque quantidadeItens ordena por agregado (COUNT sobre itens_catalogo).
     */
    @Query(value = """
            SELECT c.id FROM Catalogo c
            LEFT JOIN ItemCatalogo ic ON ic.catalogo = c AND ic.deletedAt IS NULL
            WHERE c.usuario.id = :usuarioId
            AND (:busca IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
            GROUP BY c.id
            """,
            countQuery = """
            SELECT COUNT(c.id) FROM Catalogo c
            WHERE c.usuario.id = :usuarioId
            AND (:busca IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
            """)
    Page<UUID> buscarIdsOrdenados(@Param("usuarioId") UUID usuarioId, @Param("busca") String busca, Pageable pageable);
}
