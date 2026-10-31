package com.penseprecifique.api.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClienteConsentimentoEventoRepository extends JpaRepository<ClienteConsentimentoEvento, UUID> {
    List<ClienteConsentimentoEvento> findByClienteIdOrderByRegistradoEmDesc(UUID clienteId);
}
