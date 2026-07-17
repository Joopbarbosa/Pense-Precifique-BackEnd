package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.ConfiguracaoPrecificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracaoPrecificacaoRepository extends JpaRepository<ConfiguracaoPrecificacao, UUID> {
    Optional<ConfiguracaoPrecificacao> findByUsuarioId(UUID usuarioId);
}
