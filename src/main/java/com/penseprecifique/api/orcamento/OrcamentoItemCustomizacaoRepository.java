package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrcamentoItemCustomizacaoRepository extends JpaRepository<OrcamentoItemCustomizacao, UUID> {

    List<OrcamentoItemCustomizacao> findByOrcamentoItemId(UUID orcamentoItemId);

    // Achado do teste manual (V0.10.0) — estoque/produção do orçamento precisam considerar as
    // customizações anexadas, não só o produto principal de cada item; batched pro orçamento
    // inteiro (1 query), nunca 1 lookup por item.
    List<OrcamentoItemCustomizacao> findByOrcamentoItemIdIn(List<UUID> orcamentoItemIds);

    void deleteByOrcamentoItemId(UUID orcamentoItemId);
}
