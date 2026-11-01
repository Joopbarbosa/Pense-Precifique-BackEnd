package com.penseprecifique.api.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID> {

    Optional<Lancamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Lock pessimista para recalcular o total e cancelar sem corrida com uma baixa concorrente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lancamento l WHERE l.id = :id AND l.usuario.id = :usuarioId")
    Optional<Lancamento> buscarParaAlterar(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    /** V0.19.0 (#1173) — lançamento da compra (um por compra), com lock, para substituir/cancelar parcelas na transação da compra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lancamento l WHERE l.compra.id = :compraId AND l.usuario.id = :usuarioId")
    Optional<Lancamento> buscarDaCompraParaAlterar(@Param("compraId") UUID compraId, @Param("usuarioId") UUID usuarioId);

    /** V0.19.0 (#1174) — lançamento a receber do orçamento (o do estorno do sinal tem subtipo e fica de fora), com lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lancamento l WHERE l.orcamento.id = :orcamentoId AND l.usuario.id = :usuarioId AND l.subtipo IS NULL")
    Optional<Lancamento> buscarDoOrcamentoParaAlterar(@Param("orcamentoId") UUID orcamentoId, @Param("usuarioId") UUID usuarioId);

    Optional<Lancamento> findByOrcamentoIdAndUsuarioIdAndSubtipoIsNull(UUID orcamentoId, UUID usuarioId);

    List<Lancamento> findByOrcamentoIdAndSubtipo(UUID orcamentoId, SubtipoLancamento subtipo);

    boolean existsByOrcamentoIdAndSubtipo(UUID orcamentoId, SubtipoLancamento subtipo);

    boolean existsByCompraIdAndUsuarioId(UUID compraId, UUID usuarioId);

    boolean existsByUsuarioId(UUID usuarioId);
}
