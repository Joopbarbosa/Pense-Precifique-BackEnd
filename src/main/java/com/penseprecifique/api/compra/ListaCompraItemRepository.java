package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.ListaCompraItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ListaCompraItemRepository extends JpaRepository<ListaCompraItem, UUID> {

    @EntityGraph(attributePaths = {"insumo", "fornecedor"})
    List<ListaCompraItem> findByListaIdOrderByOrdemAsc(UUID listaId);

    /** Quantidade de itens por lista (histórico). Linhas: [listaId, count]. */
    @Query("SELECT i.lista.id, COUNT(i) FROM ListaCompraItem i WHERE i.lista.id IN :listaIds GROUP BY i.lista.id")
    List<Object[]> contarPorLista(@Param("listaIds") Collection<UUID> listaIds);
}
