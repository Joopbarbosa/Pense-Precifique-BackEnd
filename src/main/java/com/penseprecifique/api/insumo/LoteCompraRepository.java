package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.LoteCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoteCompraRepository extends JpaRepository<LoteCompra, UUID> {

    Optional<LoteCompra> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}
