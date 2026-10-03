package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.FornecedorInsumo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FornecedorInsumoRepository extends JpaRepository<FornecedorInsumo, UUID> {

    Optional<FornecedorInsumo> findByFornecedorIdAndInsumoId(UUID fornecedorId, UUID insumoId);

    @EntityGraph(attributePaths = {"fornecedor", "insumo", "insumo.unidadeMedida"})
    Optional<FornecedorInsumo> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @EntityGraph(attributePaths = {"fornecedor", "insumo", "insumo.unidadeMedida"})
    List<FornecedorInsumo> findByUsuarioIdAndFornecedorId(UUID usuarioId, UUID fornecedorId);

    @EntityGraph(attributePaths = {"fornecedor", "insumo", "insumo.unidadeMedida"})
    List<FornecedorInsumo> findByUsuarioIdAndInsumoId(UUID usuarioId, UUID insumoId);

    @EntityGraph(attributePaths = {"fornecedor", "insumo"})
    List<FornecedorInsumo> findByUsuarioIdAndInsumoIdIn(UUID usuarioId, Collection<UUID> insumoIds);

    long countByUsuarioIdAndFornecedorId(UUID usuarioId, UUID fornecedorId);
}
