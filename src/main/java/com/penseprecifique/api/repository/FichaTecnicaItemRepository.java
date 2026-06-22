package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.FichaTecnicaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FichaTecnicaItemRepository extends JpaRepository<FichaTecnicaItem, UUID> {

    List<FichaTecnicaItem> findByProdutoId(UUID produtoId);

    void deleteByProdutoId(UUID produtoId);
}
