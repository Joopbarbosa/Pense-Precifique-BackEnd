package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.LoteCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoteCompraRepository extends JpaRepository<LoteCompra, UUID> {

    Optional<LoteCompra> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}
