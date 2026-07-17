package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.Producao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProducaoRepository extends JpaRepository<Producao, UUID> {

    Page<Producao> findByUsuarioIdOrderByDataProducaoDesc(UUID usuarioId, Pageable pageable);

    Optional<Producao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<Producao> findByNumeroAndUsuarioId(Integer numero, UUID usuarioId);

    Optional<Producao> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);
}
