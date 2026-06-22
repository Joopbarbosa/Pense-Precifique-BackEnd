package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovimentacaoInsumoRepository extends JpaRepository<MovimentacaoInsumo, UUID> {

    Page<MovimentacaoInsumo> findByInsumoIdOrderByCreatedAtDesc(UUID insumoId, Pageable pageable);

    List<MovimentacaoInsumo> findByReferenciaIdAndReferenciaTipo(
            UUID referenciaId, ReferenciaMovimentacaoTipo referenciaTipo);
}
