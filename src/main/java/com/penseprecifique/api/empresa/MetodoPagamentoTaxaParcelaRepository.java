package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoTaxaParcela;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MetodoPagamentoTaxaParcelaRepository extends JpaRepository<MetodoPagamentoTaxaParcela, UUID> {
    List<MetodoPagamentoTaxaParcela> findByMetodoPagamentoIdOrderByParcelaAsc(UUID metodoPagamentoId);
    void deleteByMetodoPagamentoId(UUID metodoPagamentoId);
}
