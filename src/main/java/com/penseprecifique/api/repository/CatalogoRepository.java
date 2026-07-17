package com.penseprecifique.api.repository;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogoRepository extends JpaRepository<Catalogo, UUID> {

    Optional<Catalogo> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    Optional<Catalogo> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Catalogo> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    List<Catalogo> findByUsuarioId(UUID usuarioId);

    List<Catalogo> findByUsuarioIdAndNomeContainingIgnoreCase(UUID usuarioId, String nome);

    boolean existsByUsuarioIdAndNomeIgnoreCase(UUID usuarioId, String nome);
}
