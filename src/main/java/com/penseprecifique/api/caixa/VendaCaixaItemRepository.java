package com.penseprecifique.api.caixa;

import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendaCaixaItemRepository extends JpaRepository<VendaCaixaItem, UUID> {

    List<VendaCaixaItem> findByVendaCaixaId(UUID vendaCaixaId);

    /** #560/#451 (V0.15.0) — itens de várias vendas de uma vez (indicadores do cliente). */
    @EntityGraph(attributePaths = {"itemCatalogo", "produto"})
    List<VendaCaixaItem> findByVendaCaixaIdIn(Collection<UUID> vendaIds);
}
