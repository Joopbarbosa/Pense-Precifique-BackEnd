package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoStatusProducaoRepository extends JpaRepository<HistoricoStatusProducao, UUID> {

    // Ordem cronológica (mais antiga primeiro) — exibida como linha do tempo em ProducaoDetalheResponse.
    List<HistoricoStatusProducao> findByProducaoIdOrderByDataTransicaoAsc(UUID producaoId);
}
