package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaixaTurnoRepository extends JpaRepository<CaixaTurno, UUID> {

    Optional<CaixaTurno> findByUsuarioIdAndStatus(UUID usuarioId, StatusCaixaTurno status);

    Optional<CaixaTurno> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<CaixaTurno> findByIdAndUsuarioIdAndStatus(UUID id, UUID usuarioId, StatusCaixaTurno status);
}
