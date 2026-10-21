package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoStatusProducaoRepository extends JpaRepository<HistoricoStatusProducao, UUID> {

    /** V0.18.0 (RN-NOVA-16) — das produções informadas, as que já estiveram EM_ANDAMENTO alguma vez (começaram de fato). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT DISTINCT h.producao.id FROM HistoricoStatusProducao h
            WHERE h.producao.id IN :producaoIds AND h.tipoEvento = com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao.STATUS
            AND h.statusNovo = com.penseprecifique.api.shared.domain.enums.EstadoProducao.EM_ANDAMENTO
            """)
    List<UUID> findIdsQueJaEstiveramEmAndamento(@org.springframework.data.repository.query.Param("producaoIds") java.util.Collection<UUID> producaoIds);

    // Ordem cronológica (mais antiga primeiro) — exibida como linha do tempo em ProducaoDetalheResponse.
    List<HistoricoStatusProducao> findByProducaoIdOrderByDataTransicaoAsc(UUID producaoId);

    // P-B017 (#320) — linhas ITEM_ADICIONADO/ITEM_REMOVIDO de um par orçamento+produção específico
    // (re-sincronização em vincularProducao() e reversão em desvincularProducao()).
    List<HistoricoStatusProducao> findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
            UUID producaoId, UUID referenciaOrcamentoId, TipoEventoHistoricoProducao tipoEvento);

    // P-B018 (#320) — todas as linhas ITEM_ADICIONADO/ITEM_REMOVIDO de um produto específico numa
    // produção, de todos os orçamentos que já contribuíram — usado por dividir()/criarProducaoFilha()
    // para calcular o saldo líquido por orçamento (ITEM_ADICIONADO - ITEM_REMOVIDO) antes de propagar.
    List<HistoricoStatusProducao> findByProducaoIdAndProdutoIdAndTipoEventoIn(
            UUID producaoId, UUID produtoId, List<TipoEventoHistoricoProducao> tiposEvento);

    // RN-NOVA-17 (V0.8.3, #375+308) — linhas ITEM_ADICIONADO de UM produto específico contribuído
    // por UM orçamento específico — granularidade usada pela remoção "por produto" em produção já
    // EM_ANDAMENTO/TRAVADA (removerProdutoDeProducaoAtiva), diferente de
    // findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento (todos os produtos daquele orçamento).
    List<HistoricoStatusProducao> findByProducaoIdAndReferenciaOrcamentoIdAndProdutoIdAndTipoEvento(
            UUID producaoId, UUID referenciaOrcamentoId, UUID produtoId, TipoEventoHistoricoProducao tipoEvento);
}
