package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.domain.entity.VinculoItemNota;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VinculoItemNotaRepository extends JpaRepository<VinculoItemNota, UUID>, JpaSpecificationExecutor<VinculoItemNota> {

    @Override
    @EntityGraph(attributePaths = {"insumo", "insumo.unidadeMedida"})
    Page<VinculoItemNota> findAll(Specification<VinculoItemNota> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"insumo", "insumo.unidadeMedida"})
    Optional<VinculoItemNota> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @EntityGraph(attributePaths = {"insumo", "insumo.unidadeMedida"})
    List<VinculoItemNota> findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizadoIn(
            UUID usuarioId, String emitenteCnpj, Collection<String> nomesNormalizados);

    Optional<VinculoItemNota> findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(
            UUID usuarioId, String emitenteCnpj, String nomeNormalizado);

    /**
     * #718 (RN-NOVA-26) — vínculos de outros fornecedores (CNPJ diferente) com os mesmos nomes de item:
     * só os não ignorados, com insumo ativo e não excluído. {@code cnpjExcluido} vazio vale para qualquer CNPJ.
     */
    @Query("SELECT v FROM VinculoItemNota v JOIN FETCH v.insumo i LEFT JOIN FETCH i.unidadeMedida " +
            "WHERE v.usuario.id = :usuarioId AND v.nomeItemNormalizado IN :nomes AND v.ignorar = false " +
            "AND v.emitenteCnpj <> :cnpjExcluido AND i.ativo = true AND i.deletedAt IS NULL ORDER BY v.updatedAt DESC")
    List<VinculoItemNota> findDeOutrosFornecedores(@Param("usuarioId") UUID usuarioId,
            @Param("nomes") Collection<String> nomes, @Param("cnpjExcluido") String cnpjExcluido);
}
