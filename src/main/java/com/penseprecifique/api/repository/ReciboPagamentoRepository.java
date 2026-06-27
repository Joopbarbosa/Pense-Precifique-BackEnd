package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.ReciboPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReciboPagamentoRepository extends JpaRepository<ReciboPagamento, UUID> {

    Optional<ReciboPagamento> findByOrcamentoId(UUID orcamentoId);
}
