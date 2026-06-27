package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.OrcamentoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrcamentoItemRepository extends JpaRepository<OrcamentoItem, UUID> {

    List<OrcamentoItem> findByOrcamentoId(UUID orcamentoId);

    void deleteByOrcamentoId(UUID orcamentoId);
}
