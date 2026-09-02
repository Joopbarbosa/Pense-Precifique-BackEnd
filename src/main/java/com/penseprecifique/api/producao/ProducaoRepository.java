package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * #158/RN-NOVA-6 — busca por numero (PRD-N) OU nome de produto (via producao_produtos), filtro por
     * estado e por intervalo de dataInicio (#184/#192 — RN-NOVA-2, usado tanto pela Listagem quanto
     * pelo Kanban, já que os dois consomem o mesmo GET /producoes). Retorna só os IDs, ordenados
     * conforme o Sort do Pageable (allowlist validada e resolvida em ProducaoService.resolverPageableOrdenado).
     * GROUP BY p.id (em vez de DISTINCT) porque "produto"/"quantidade" ordenam por agregado
     * (MIN/SUM sobre producao_produtos) — não dá pra usar DISTINCT com ORDER BY em coluna agregada
     * não presente no SELECT. Passo 2 (buscar as entidades completas na mesma ordem dos IDs) fica no
     * Service, via findAllById + reordenação manual (findAllById não preserva ordem).
     *
     * Bug pré-existente corrigido na Onda 3 (achado ao rodar a suíte completa pela primeira vez, ver
     * fix de pom.xml/Surefire): CAST em **ambas** as ocorrências de dataInicioDe/dataInicioAte (a
     * checagem "IS NULL" e a comparação) quebrava com "cannot cast type bytea to date"; removendo o
     * CAST de **ambas** quebrava a comparação com "could not determine data type of parameter $N" (o
     * Postgres não consegue inferir o tipo de um parâmetro usado só numa checagem "? IS NULL" sem
     * contexto de coluna). Fix: CAST **só** na checagem IS NULL (que não tem contexto pra inferir
     * sozinha); a comparação (`p.dataInicio >= :dataInicioDe`) já resolve o tipo pela coluna, sem CAST.
     *
     * <p>Bug pré-existente corrigido em P-B003 (V0.8.3, achado de P-B002): a navegação de associação
     * {@code pp.produto.nome} (usada só no filtro opcional de busca por nome), mesmo com {@code pp}
     * vindo de um {@code LEFT JOIN}, era traduzida pelo Hibernate para um {@code INNER JOIN} implícito
     * contra {@code produtos} — quando {@code pp} é {@code NULL} (produção sem nenhum
     * {@code ProducaoProduto}), esse INNER JOIN não casava com nada e a linha inteira desaparecia do
     * resultado, mesmo sem nenhum filtro de busca ativo. Corrigido declarando o segundo
     * {@code LEFT JOIN pp.produto prod} explicitamente — navegação de associação a partir de uma linha
     * já opcional (`pp`) precisa ser declarada como LEFT JOIN também, nunca fica implícita.
     */
    @Query(value = """
            SELECT p.id FROM Producao p
            LEFT JOIN ProducaoProduto pp ON pp.producao = p
            LEFT JOIN pp.produto prod
            WHERE p.usuario.id = :usuarioId
            AND (:estado IS NULL OR p.estado = :estado)
            AND (CAST(:dataInicioDe AS date) IS NULL OR p.dataInicio >= :dataInicioDe)
            AND (CAST(:dataInicioAte AS date) IS NULL OR p.dataInicio <= :dataInicioAte)
            AND (
                (:buscaNumero IS NULL AND :buscaNome IS NULL)
                OR (:buscaNumero IS NOT NULL AND p.numero = :buscaNumero)
                OR (:buscaNome IS NOT NULL AND LOWER(prod.nome) LIKE LOWER(CONCAT('%', CAST(:buscaNome AS string), '%')))
            )
            GROUP BY p.id
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM Producao p
            LEFT JOIN ProducaoProduto pp ON pp.producao = p
            LEFT JOIN pp.produto prod
            WHERE p.usuario.id = :usuarioId
            AND (:estado IS NULL OR p.estado = :estado)
            AND (CAST(:dataInicioDe AS date) IS NULL OR p.dataInicio >= :dataInicioDe)
            AND (CAST(:dataInicioAte AS date) IS NULL OR p.dataInicio <= :dataInicioAte)
            AND (
                (:buscaNumero IS NULL AND :buscaNome IS NULL)
                OR (:buscaNumero IS NOT NULL AND p.numero = :buscaNumero)
                OR (:buscaNome IS NOT NULL AND LOWER(prod.nome) LIKE LOWER(CONCAT('%', CAST(:buscaNome AS string), '%')))
            )
            """)
    Page<UUID> buscarIdsOrdenados(@Param("usuarioId") UUID usuarioId,
                                  @Param("estado") EstadoProducao estado,
                                  @Param("dataInicioDe") LocalDate dataInicioDe,
                                  @Param("dataInicioAte") LocalDate dataInicioAte,
                                  @Param("buscaNumero") Integer buscaNumero,
                                  @Param("buscaNome") String buscaNome,
                                  Pageable pageable);
}
