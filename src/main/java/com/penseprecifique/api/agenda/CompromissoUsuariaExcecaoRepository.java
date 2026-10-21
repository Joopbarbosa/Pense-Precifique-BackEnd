package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.domain.entity.CompromissoUsuariaExcecao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompromissoUsuariaExcecaoRepository extends JpaRepository<CompromissoUsuariaExcecao, UUID> {

    Optional<CompromissoUsuariaExcecao> findByCompromissoIdAndDataOriginal(UUID compromissoId, LocalDate dataOriginal);

    List<CompromissoUsuariaExcecao> findByCompromissoId(UUID compromissoId);

    @Query("""
            SELECT e FROM CompromissoUsuariaExcecao e
            LEFT JOIN FETCH e.orcamento LEFT JOIN FETCH e.insumo
            WHERE e.compromisso.id IN :ids
            """)
    List<CompromissoUsuariaExcecao> findDosCompromissosComVinculos(@Param("ids") Collection<UUID> ids);
}
