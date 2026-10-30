package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProducaoProdutoRepository extends JpaRepository<ProducaoProduto, UUID> {

    List<ProducaoProduto> findByProducaoId(UUID producaoId);

    /** V0.18.0 (RN-NOVA-16) — produtos de várias produções de uma vez, com o produto, para o título na Agenda. */
    @org.springframework.data.jpa.repository.Query("SELECT pp FROM ProducaoProduto pp JOIN FETCH pp.produto WHERE pp.producao.id IN :producaoIds")
    List<ProducaoProduto> findComProdutoByProducaoIdIn(@org.springframework.data.repository.query.Param("producaoIds") java.util.Collection<UUID> producaoIds);

    Optional<ProducaoProduto> findByProducaoIdAndProdutoId(UUID producaoId, UUID produtoId);
}
