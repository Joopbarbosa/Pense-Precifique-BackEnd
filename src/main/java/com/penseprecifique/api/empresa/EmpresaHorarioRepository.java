package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.EmpresaHorario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmpresaHorarioRepository extends JpaRepository<EmpresaHorario, UUID> {
    List<EmpresaHorario> findByEmpresaIdOrderByDiaSemanaAsc(UUID empresaId);
    void deleteByEmpresaId(UUID empresaId);
}
