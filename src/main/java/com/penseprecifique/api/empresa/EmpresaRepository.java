package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    Optional<Empresa> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId);
}
