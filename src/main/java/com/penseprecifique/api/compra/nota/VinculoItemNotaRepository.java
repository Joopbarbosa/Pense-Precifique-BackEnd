package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.domain.entity.VinculoItemNota;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VinculoItemNotaRepository extends JpaRepository<VinculoItemNota, UUID> {

    @EntityGraph(attributePaths = {"insumo", "insumo.unidadeMedida"})
    List<VinculoItemNota> findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizadoIn(
            UUID usuarioId, String emitenteCnpj, Collection<String> nomesNormalizados);

    Optional<VinculoItemNota> findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(
            UUID usuarioId, String emitenteCnpj, String nomeNormalizado);

    /** RN-NOVA-18 (CEN-NOVO-52) — excluir insumo em rascunho desfaz os vínculos que apontavam para ele. */
    @Modifying
    @Query("DELETE FROM VinculoItemNota v WHERE v.insumo.id = :insumoId")
    int deleteByInsumoId(@Param("insumoId") UUID insumoId);
}
