package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendaCaixaRepository extends JpaRepository<VendaCaixa, UUID> {

    Optional<VendaCaixa> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<VendaCaixa> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    List<VendaCaixa> findByCaixaTurnoIdOrderByDataVendaDesc(UUID caixaTurnoId);

    /** #560 (V0.15.0) — todas as vendas de um cadastro (histórico e indicadores do cliente). */
    List<VendaCaixa> findByClienteIdAndUsuarioId(UUID clienteId, UUID usuarioId);

    /** #577/RN-NOVA-27 (V0.15.0) — vendas do Caixa no período do CMV. */
    List<VendaCaixa> findByUsuarioIdAndStatusAndDataVendaBetween(
            UUID usuarioId, StatusVendaCaixa status, LocalDateTime de, LocalDateTime ate);
}
