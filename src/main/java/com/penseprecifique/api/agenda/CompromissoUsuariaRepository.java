package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.domain.entity.CompromissoUsuaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompromissoUsuariaRepository extends JpaRepository<CompromissoUsuaria, UUID> {

    Optional<CompromissoUsuaria> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Todos os compromissos da conta, com os vínculos (a Agenda gera as ocorrências em memória a partir deles). */
    @Query("""
            SELECT c FROM CompromissoUsuaria c
            LEFT JOIN FETCH c.orcamento LEFT JOIN FETCH c.insumo
            WHERE c.usuario.id = :usuarioId
            """)
    List<CompromissoUsuaria> findDaContaComVinculos(@Param("usuarioId") UUID usuarioId);
}
