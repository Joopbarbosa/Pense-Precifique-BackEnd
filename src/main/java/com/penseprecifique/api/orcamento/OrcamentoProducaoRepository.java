package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoProducaoRepository extends JpaRepository<OrcamentoProducao, UUID> {

    List<OrcamentoProducao> findByOrcamentoId(UUID orcamentoId);

    Optional<OrcamentoProducao> findByOrcamentoIdAndProducaoId(UUID orcamentoId, UUID producaoId);

    // RN-NOVA-15 (V0.8.3, #375+308) — seção "Orçamentos vinculados" no Detalhe de Produção.
    List<OrcamentoProducao> findByProducaoId(UUID producaoId);
}
