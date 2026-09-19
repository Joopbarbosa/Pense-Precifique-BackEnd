package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetodoPagamentoConfiguravelRepository extends JpaRepository<MetodoPagamentoConfiguravel, UUID> {

    List<MetodoPagamentoConfiguravel> findByUsuarioIdOrderByOrdemAscTipoAsc(UUID usuarioId);

    Optional<MetodoPagamentoConfiguravel> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByUsuarioIdAndTipoAndNomeIgnoreCase(UUID usuarioId, TipoMetodoPagamento tipo, String nome);
}
