package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.Orcamento;
import com.penseprecifique.api.domain.enums.StatusOrcamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository extends JpaRepository<Orcamento, UUID> {

    Page<Orcamento> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Orcamento> findByUsuarioIdAndStatusAndDeletedAtIsNull(UUID usuarioId, StatusOrcamento status, Pageable pageable);

    Optional<Orcamento> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);
}
