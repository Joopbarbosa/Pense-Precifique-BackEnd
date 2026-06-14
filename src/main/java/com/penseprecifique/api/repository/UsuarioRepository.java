package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByIdAndDeletedAtIsNull(UUID id);
}
