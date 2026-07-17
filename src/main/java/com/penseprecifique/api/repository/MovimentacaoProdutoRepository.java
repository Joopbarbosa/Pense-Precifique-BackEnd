package com.penseprecifique.api.repository;

import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoProdutoRepository extends JpaRepository<MovimentacaoProduto, UUID> {

    Page<MovimentacaoProduto> findByProdutoIdOrderByCreatedAtDesc(UUID produtoId, Pageable pageable);

    Optional<MovimentacaoProduto> findByProdutoIdAndMotivoAndReferenciaIdAndTipo(
            UUID produtoId, MotivoMovimentacaoProduto motivo, UUID referenciaId, TipoMovimentacaoProduto tipo);
}
