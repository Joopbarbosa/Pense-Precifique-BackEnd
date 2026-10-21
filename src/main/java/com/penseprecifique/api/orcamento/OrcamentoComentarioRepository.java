package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.OrcamentoComentario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrcamentoComentarioRepository extends JpaRepository<OrcamentoComentario, UUID> {

    Page<OrcamentoComentario> findByOrcamentoId(UUID orcamentoId, Pageable pageable);
}
