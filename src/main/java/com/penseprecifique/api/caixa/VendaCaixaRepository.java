package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendaCaixaRepository extends JpaRepository<VendaCaixa, UUID> {

    Optional<VendaCaixa> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<VendaCaixa> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    List<VendaCaixa> findByCaixaTurnoIdOrderByDataVendaDesc(UUID caixaTurnoId);
}
