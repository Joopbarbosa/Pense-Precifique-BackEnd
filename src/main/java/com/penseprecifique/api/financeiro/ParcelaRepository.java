package com.penseprecifique.api.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelaRepository extends JpaRepository<Parcela, UUID> {

    /** Parcela da usuária com lock (baixa e desfazer baixa não correm juntas). Id de outra usuária = vazio (404). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcela p JOIN FETCH p.lancamento WHERE p.id = :id AND p.usuario.id = :usuarioId")
    Optional<Parcela> buscarParaAlterar(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    @EntityGraph(attributePaths = {"lancamento", "metodoPagamento"})
    @Query("SELECT p FROM Parcela p WHERE p.id = :id AND p.usuario.id = :usuarioId")
    Optional<Parcela> buscarDaUsuaria(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    List<Parcela> findByLancamentoIdOrderByNumeroAsc(UUID lancamentoId);

    /**
     * RN-NOVA-4 / DT-NOVA-2 — uma linha por parcela. Vencidas (Em aberto com vencimento anterior a {@code hoje}) primeiro,
     * depois vencimento crescente. {@code somenteVencidas} é filtro de consulta; "Vencida" nunca é gravada.
     */
    @EntityGraph(attributePaths = {"lancamento", "metodoPagamento"})
    @Query(value = "SELECT p FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.vencimento BETWEEN :de AND :ate AND p.situacao IN :situacoes " +
            "AND (:somenteVencidas = false OR p.vencimento < :hoje) " +
            "ORDER BY CASE WHEN p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.EM_ABERTO AND p.vencimento < :hoje THEN 0 ELSE 1 END, " +
            "p.vencimento, p.lancamento.createdAt, p.numero",
            countQuery = "SELECT COUNT(p) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
                    "AND p.vencimento BETWEEN :de AND :ate AND p.situacao IN :situacoes " +
                    "AND (:somenteVencidas = false OR p.vencimento < :hoje)")
    Page<Parcela> listar(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo,
                         @Param("de") LocalDate de, @Param("ate") LocalDate ate,
                         @Param("situacoes") Collection<SituacaoParcela> situacoes,
                         @Param("somenteVencidas") boolean somenteVencidas, @Param("hoje") LocalDate hoje, Pageable pageable);

    /** Total Em aberto do período (RN-NOVA-4). */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.EM_ABERTO AND p.vencimento BETWEEN :de AND :ate")
    BigDecimal totalEmAberto(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo,
                             @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Total vencido do período: Em aberto com vencimento anterior a hoje. */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.EM_ABERTO AND p.vencimento BETWEEN :de AND :ate " +
            "AND p.vencimento < :hoje")
    BigDecimal totalVencido(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo,
                            @Param("de") LocalDate de, @Param("ate") LocalDate ate, @Param("hoje") LocalDate hoje);

    /** "n de N": N = parcelas não canceladas de cada lançamento (RN-NOVA-1). */
    @Query("SELECT p.lancamento.id, COUNT(p) FROM Parcela p WHERE p.lancamento.id IN :ids " +
            "AND p.situacao <> com.penseprecifique.api.financeiro.SituacaoParcela.CANCELADA GROUP BY p.lancamento.id")
    List<Object[]> contarAtivasPorLancamento(@Param("ids") Collection<UUID> ids);
}
