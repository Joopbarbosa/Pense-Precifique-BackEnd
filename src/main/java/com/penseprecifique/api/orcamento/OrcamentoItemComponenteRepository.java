package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoItemComponente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrcamentoItemComponenteRepository extends JpaRepository<OrcamentoItemComponente, UUID> {

    List<OrcamentoItemComponente> findByOrcamentoItemId(UUID orcamentoItemId);

    List<OrcamentoItemComponente> findByOrcamentoItemIdIn(List<UUID> orcamentoItemIds);

    void deleteByOrcamentoItemId(UUID orcamentoItemId);
}
