package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProducaoRepository extends JpaRepository<Producao, UUID> {

    Page<Producao> findByUsuarioIdOrderByNumeroDesc(UUID usuarioId, Pageable pageable);

    Optional<Producao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<Producao> findByNumeroAndUsuarioId(Integer numero, UUID usuarioId);

    Optional<Producao> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    // RN-073/UC-036 — produções filhas de uma divisão (DIVISAO) ou agrupamento (AGRUPAMENTO).
    List<Producao> findByProducaoOrigemId(UUID producaoOrigemId);

    // RN-076/#123 — busca por numero (PRD-N) OU nome de produto (via producao_produtos), filtro por
    // estado, ordenado por dataInicio DESC (produções legadas sem dataInicio ficam por último).
    @Query("""
            SELECT DISTINCT p FROM Producao p
            LEFT JOIN ProducaoProduto pp ON pp.producao = p
            WHERE p.usuario.id = :usuarioId
            AND (:estado IS NULL OR p.estado = :estado)
            AND (
                (:buscaNumero IS NULL AND :buscaNome IS NULL)
                OR (:buscaNumero IS NOT NULL AND p.numero = :buscaNumero)
                OR (:buscaNome IS NOT NULL AND LOWER(pp.produto.nome) LIKE LOWER(CONCAT('%', CAST(:buscaNome AS string), '%')))
            )
            ORDER BY p.dataInicio DESC NULLS LAST, p.numero DESC
            """)
    Page<Producao> buscar(@Param("usuarioId") UUID usuarioId,
                           @Param("estado") EstadoProducao estado,
                           @Param("buscaNumero") Integer buscaNumero,
                           @Param("buscaNome") String buscaNome,
                           Pageable pageable);

    /**
     * #158/RN-NOVA-6 — mesmo filtro de {@link #buscar}, mas retorna só os IDs, ordenados conforme o
     * Sort do Pageable (allowlist validada e resolvida em ProducaoService.resolverPageableOrdenado).
     * GROUP BY p.id (em vez de DISTINCT) porque "produto"/"quantidade" ordenam por agregado
     * (MIN/SUM sobre producao_produtos) — não dá pra usar DISTINCT com ORDER BY em coluna agregada
     * não presente no SELECT. Passo 2 (buscar as entidades completas na mesma ordem dos IDs) fica no
     * Service, via findAllById + reordenação manual (findAllById não preserva ordem).
     */
    @Query(value = """
            SELECT p.id FROM Producao p
            LEFT JOIN ProducaoProduto pp ON pp.producao = p
            WHERE p.usuario.id = :usuarioId
            AND (:estado IS NULL OR p.estado = :estado)
            AND (
                (:buscaNumero IS NULL AND :buscaNome IS NULL)
                OR (:buscaNumero IS NOT NULL AND p.numero = :buscaNumero)
                OR (:buscaNome IS NOT NULL AND LOWER(pp.produto.nome) LIKE LOWER(CONCAT('%', CAST(:buscaNome AS string), '%')))
            )
            GROUP BY p.id
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM Producao p
            LEFT JOIN ProducaoProduto pp ON pp.producao = p
            WHERE p.usuario.id = :usuarioId
            AND (:estado IS NULL OR p.estado = :estado)
            AND (
                (:buscaNumero IS NULL AND :buscaNome IS NULL)
                OR (:buscaNumero IS NOT NULL AND p.numero = :buscaNumero)
                OR (:buscaNome IS NOT NULL AND LOWER(pp.produto.nome) LIKE LOWER(CONCAT('%', CAST(:buscaNome AS string), '%')))
            )
            """)
    Page<UUID> buscarIdsOrdenados(@Param("usuarioId") UUID usuarioId,
                                  @Param("estado") EstadoProducao estado,
                                  @Param("buscaNumero") Integer buscaNumero,
                                  @Param("buscaNome") String buscaNome,
                                  Pageable pageable);
}
