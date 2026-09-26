package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.ListaCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListaCompraRepository extends JpaRepository<ListaCompra, UUID> {

    Optional<ListaCompra> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    Optional<ListaCompra> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Page<ListaCompra> findByUsuarioId(UUID usuarioId, Pageable pageable);
}
