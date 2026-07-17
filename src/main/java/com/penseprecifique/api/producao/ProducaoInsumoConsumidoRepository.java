package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.ProducaoInsumoConsumido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProducaoInsumoConsumidoRepository extends JpaRepository<ProducaoInsumoConsumido, UUID> {

    List<ProducaoInsumoConsumido> findByProducaoId(UUID producaoId);
}
