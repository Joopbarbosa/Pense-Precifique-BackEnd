package com.penseprecifique.api.repository;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository extends JpaRepository<Orcamento, UUID> {

    Page<Orcamento> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Orcamento> findByUsuarioIdAndStatusAndDeletedAtIsNull(UUID usuarioId, StatusOrcamento status, Pageable pageable);

    Page<Orcamento> findByUsuarioIdAndClienteNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, String clienteNome, Pageable pageable);

    Page<Orcamento> findByUsuarioIdAndStatusAndClienteNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, StatusOrcamento status, String clienteNome, Pageable pageable);

    Optional<Orcamento> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    Optional<Orcamento> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    long countByUsuarioIdAndDeletedAtIsNull(UUID usuarioId);

    long countByUsuarioIdAndStatusInAndDeletedAtIsNull(UUID usuarioId, List<StatusOrcamento> statuses);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Orcamento o WHERE o.usuario.id = :uid AND o.status = :status AND o.deletedAt IS NULL")
    BigDecimal sumTotalByStatus(@Param("uid") UUID uid, @Param("status") StatusOrcamento status);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Orcamento o WHERE o.usuario.id = :uid AND o.status = :status AND o.updatedAt >= :inicio AND o.updatedAt < :fim AND o.deletedAt IS NULL")
    BigDecimal sumTotalByStatusAndPeriodo(@Param("uid") UUID uid, @Param("status") StatusOrcamento status, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Orcamento> findTop5ByUsuarioIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID usuarioId);
}
