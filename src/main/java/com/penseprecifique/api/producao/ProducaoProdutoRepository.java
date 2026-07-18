package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProducaoProdutoRepository extends JpaRepository<ProducaoProduto, UUID> {

    List<ProducaoProduto> findByProducaoId(UUID producaoId);
}
