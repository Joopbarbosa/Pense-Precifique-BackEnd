package com.penseprecifique.api.repository;

import com.penseprecifique.api.shared.domain.entity.ReciboEstorno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReciboEstornoRepository extends JpaRepository<ReciboEstorno, UUID> {

    Optional<ReciboEstorno> findByOrcamentoId(UUID orcamentoId);
}
