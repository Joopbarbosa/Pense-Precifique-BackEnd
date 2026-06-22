package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.MovimentacaoProduto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovimentacaoProdutoRepository extends JpaRepository<MovimentacaoProduto, UUID> {

    Page<MovimentacaoProduto> findByProdutoIdOrderByCreatedAtDesc(UUID produtoId, Pageable pageable);
}
