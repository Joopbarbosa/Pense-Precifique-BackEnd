package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FichaTecnicaItemRepository extends JpaRepository<FichaTecnicaItem, UUID> {

    List<FichaTecnicaItem> findByProdutoId(UUID produtoId);

    void deleteByProdutoId(UUID produtoId);

    @Query("SELECT DISTINCT f.produto FROM FichaTecnicaItem f WHERE f.insumo.id = :insumoId AND f.produto.deletedAt IS NULL")
    List<Produto> findProdutosByInsumoId(@Param("insumoId") UUID insumoId);
}
