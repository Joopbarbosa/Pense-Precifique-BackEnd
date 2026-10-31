package com.penseprecifique.api.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID> {

    Optional<Lancamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Lock pessimista para recalcular o total e cancelar sem corrida com uma baixa concorrente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lancamento l WHERE l.id = :id AND l.usuario.id = :usuarioId")
    Optional<Lancamento> buscarParaAlterar(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    boolean existsByUsuarioId(UUID usuarioId);
}
