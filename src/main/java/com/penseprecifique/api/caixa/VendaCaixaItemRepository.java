package com.penseprecifique.api.caixa;

import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VendaCaixaItemRepository extends JpaRepository<VendaCaixaItem, UUID> {

    /** #763: sempre na ordem em que os itens foram lançados na venda. */
    @Query("SELECT i FROM VendaCaixaItem i WHERE i.vendaCaixa.id = :vendaCaixaId ORDER BY i.createdAt, i.id")
    List<VendaCaixaItem> findByVendaCaixaId(@Param("vendaCaixaId") UUID vendaCaixaId);

    /** #560/#451 (V0.15.0) — itens de várias vendas de uma vez (indicadores do cliente). */
    @EntityGraph(attributePaths = {"itemCatalogo", "produto"})
    @Query("SELECT i FROM VendaCaixaItem i WHERE i.vendaCaixa.id IN :vendaIds ORDER BY i.createdAt, i.id")
    List<VendaCaixaItem> findByVendaCaixaIdIn(@Param("vendaIds") Collection<UUID> vendaIds);
}
