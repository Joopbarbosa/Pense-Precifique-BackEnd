package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrcamentoItemCustomizacaoRepository extends JpaRepository<OrcamentoItemCustomizacao, UUID> {

    List<OrcamentoItemCustomizacao> findByOrcamentoItemId(UUID orcamentoItemId);

    void deleteByOrcamentoItemId(UUID orcamentoItemId);
}
