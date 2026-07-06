package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.ItemCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemCatalogoRepository extends JpaRepository<ItemCatalogo, UUID> {

    List<ItemCatalogo> findByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);

    List<ItemCatalogo> findByProdutoIdAndDeletedAtIsNull(UUID produtoId);

    Optional<ItemCatalogo> findByIdAndDeletedAtIsNull(UUID id);

    long countByCatalogoIdAndDeletedAtIsNull(UUID catalogoId);
}
