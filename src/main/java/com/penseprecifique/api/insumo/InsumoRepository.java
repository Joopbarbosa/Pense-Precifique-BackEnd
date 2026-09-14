package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    Page<Insumo> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Insumo> findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
            UUID usuarioId, String nome, Pageable pageable);

    Optional<Insumo> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    boolean existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
            String nome, String marca, UUID usuarioId);

    boolean existsByNomeAndMarcaAndUsuarioIdAndIdNotAndDeletedAtIsNull(
            String nome, String marca, UUID usuarioId, UUID id);

    Optional<Insumo> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    // RN-NOVA-4 (V0.10.0, #336) — contadores por filtro, agregados no backend (não sobre a janela
    // paginada já carregada no cliente). Mesmos critérios exatos já usados client-side em
    // ListaInsumosPage.tsx (isLow/isNegative/isPositive), replicados aqui como fonte de verdade.
    long countByUsuarioIdAndDeletedAtIsNull(UUID usuarioId);

    long countByUsuarioIdAndAtivoAndDeletedAtIsNull(UUID usuarioId, boolean ativo);

    @Query("SELECT COUNT(i) FROM Insumo i WHERE i.usuario.id = :usuarioId AND i.deletedAt IS NULL " +
            "AND i.ativo = true AND i.estoqueMinimo IS NOT NULL AND i.estoqueAtual < i.estoqueMinimo")
    long contarEstoqueBaixo(@Param("usuarioId") UUID usuarioId);

    long countByUsuarioIdAndDeletedAtIsNullAndEstoqueAtualLessThan(UUID usuarioId, java.math.BigDecimal valor);

    long countByUsuarioIdAndDeletedAtIsNullAndEstoqueAtualGreaterThan(UUID usuarioId, java.math.BigDecimal valor);
}
