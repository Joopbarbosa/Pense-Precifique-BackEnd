package com.penseprecifique.api.unidademedida;

import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnidadeMedidaRepository extends JpaRepository<UnidadeMedida, UUID> {

    List<UnidadeMedida> findByUsuarioIdAndDeletedAtIsNullOrderByNomeAsc(UUID usuarioId);

    Optional<UnidadeMedida> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    // Usado por fixtures de teste (find-or-create) — não é fluxo de produção.
    Optional<UnidadeMedida> findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(UUID usuarioId, String sigla);

    boolean existsByUsuarioIdAndDeletedAtIsNullAndNomeIgnoreCase(UUID usuarioId, String nome);

    boolean existsByUsuarioIdAndDeletedAtIsNullAndSiglaIgnoreCase(UUID usuarioId, String sigla);

    boolean existsByUsuarioIdAndDeletedAtIsNullAndNomeIgnoreCaseAndIdNot(UUID usuarioId, String nome, UUID id);

    boolean existsByUsuarioIdAndDeletedAtIsNullAndSiglaIgnoreCaseAndIdNot(UUID usuarioId, String sigla, UUID id);
}
