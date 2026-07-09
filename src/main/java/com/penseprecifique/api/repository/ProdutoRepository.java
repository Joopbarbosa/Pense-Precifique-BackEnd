package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.enums.TipoProduto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    Page<Produto> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Produto> findByUsuarioIdAndTipoAndDeletedAtIsNull(UUID usuarioId, TipoProduto tipo, Pageable pageable);

    Page<Produto> findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, String nome, Pageable pageable);

    Page<Produto> findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, TipoProduto tipo, String nome, Pageable pageable);

    Optional<Produto> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    List<Produto> findByUsuarioIdAndTipoAndDeletedAtIsNull(UUID usuarioId, TipoProduto tipo);

    Optional<Produto> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);
}
