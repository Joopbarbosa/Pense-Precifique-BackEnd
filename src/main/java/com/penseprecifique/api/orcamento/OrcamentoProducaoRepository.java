package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoProducaoRepository extends JpaRepository<OrcamentoProducao, UUID> {

    List<OrcamentoProducao> findByOrcamentoId(UUID orcamentoId);

    Optional<OrcamentoProducao> findByOrcamentoIdAndProducaoId(UUID orcamentoId, UUID producaoId);

    // RN-NOVA-15 (V0.8.3, #375+308) — seção "Orçamentos vinculados" no Detalhe de Produção.
    List<OrcamentoProducao> findByProducaoId(UUID producaoId);

    // RN-NOVA-16 (V0.8.3, #375+308, P-B002) — mesmo campo em lote para a Listagem/Kanban de
    // Produção: 1 query batched por página, nunca 1 findByProducaoId por linha.
    List<OrcamentoProducao> findByProducaoIdIn(List<UUID> producaoIds);

    /**
     * RN-NOVA-26 (V0.8.3, #319+387) — vínculos ativos (produção em estado não-terminal) de um
     * orçamento, por produto coberto — 1 query batched para o orçamento inteiro (nunca 1
     * findByProducaoId por vínculo). Cada linha: [produtoId, producaoId, producaoNumero].
     *
     * <p>JOIN explícito em cada salto (op.producao e ProducaoProduto), nenhum LEFT JOIN — aqui só
     * interessam produções que de fato cobrem algum produto (diferente do cenário de P-B003, que
     * precisava listar produções mesmo sem nenhum ProducaoProduto e por isso exigia LEFT JOIN
     * declarado explicitamente em cada hop para não virar INNER JOIN implícito). Como o objetivo
     * aqui é achar produções que cobrem um produto específico, INNER JOIN é o comportamento
     * correto, não um risco de linha desaparecida.
     */
    @Query("""
        SELECT pp.produto.id, pr.id, pr.numero
        FROM OrcamentoProducao op
        JOIN op.producao pr
        JOIN ProducaoProduto pp ON pp.producao = pr
        WHERE op.orcamento.id = :orcamentoId
        AND pr.estado IN :estadosNaoTerminais
    """)
    List<Object[]> buscarVinculosAtivosPorProduto(@Param("orcamentoId") UUID orcamentoId,
                                                    @Param("estadosNaoTerminais") List<EstadoProducao> estadosNaoTerminais);
}
