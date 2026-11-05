package com.penseprecifique.api.orcamento.descritivo;

import com.penseprecifique.api.shared.domain.entity.OrcamentoDescritivoOpcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrcamentoDescritivoOpcaoRepository extends JpaRepository<OrcamentoDescritivoOpcao, UUID> {

    List<OrcamentoDescritivoOpcao> findByOrcamentoIdOrderByOrdemAsc(UUID orcamentoId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM OrcamentoDescritivoOpcao op WHERE op.orcamento.id = :orcamentoId")
    void deleteByOrcamentoId(@Param("orcamentoId") UUID orcamentoId);

    /** Contagem de opções por orçamento, em lote (lista). Cada linha: {orcamentoId, quantidade}. */
    @Query("SELECT op.orcamento.id, COUNT(op) FROM OrcamentoDescritivoOpcao op WHERE op.orcamento.id IN :ids GROUP BY op.orcamento.id")
    List<Object[]> contarPorOrcamento(@Param("ids") Collection<UUID> ids);
}
