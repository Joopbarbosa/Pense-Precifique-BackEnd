package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoInsumoRepository extends JpaRepository<MovimentacaoInsumo, UUID> {

    Page<MovimentacaoInsumo> findByInsumoIdOrderByCreatedAtDesc(UUID insumoId, Pageable pageable);

    List<MovimentacaoInsumo> findByReferenciaIdAndReferenciaTipo(
            UUID referenciaId, ReferenciaMovimentacaoTipo referenciaTipo);

    Optional<MovimentacaoInsumo> findByInsumoIdAndMotivoAndReferenciaId(
            UUID insumoId, MotivoMovimentacaoInsumo motivo, UUID referenciaId);
}
