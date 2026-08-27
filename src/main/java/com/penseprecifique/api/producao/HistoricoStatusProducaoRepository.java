package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoStatusProducaoRepository extends JpaRepository<HistoricoStatusProducao, UUID> {

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
}
