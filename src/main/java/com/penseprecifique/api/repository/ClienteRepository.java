package com.penseprecifique.api.repository;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Page<Cliente> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Optional<Cliente> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    Page<Cliente> findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, String nome, Pageable pageable);

    Optional<Cliente> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);
}
