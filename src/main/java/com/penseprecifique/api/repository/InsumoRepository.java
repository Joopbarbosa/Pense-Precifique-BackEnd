package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.Insumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    Page<Insumo> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Insumo> findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, String nome, Pageable pageable);

    Optional<Insumo> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    boolean existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
            String nome, String marca, UUID usuarioId);

    boolean existsByNomeAndMarcaAndUsuarioIdAndIdNotAndDeletedAtIsNull(
            String nome, String marca, UUID usuarioId, UUID id);

    Optional<Insumo> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);
}
