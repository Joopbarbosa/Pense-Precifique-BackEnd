package com.penseprecifique.api.whatsapp;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsappEnvioRepository extends JpaRepository<WhatsappEnvio, UUID> {

    long countByUsuarioIdAndResultadoAndEnviadoEmGreaterThanEqualAndEnviadoEmLessThan(
            UUID usuarioId, ResultadoEnvio resultado, Instant inicio, Instant fim);

    Optional<WhatsappEnvio> findFirstByUsuarioIdAndResultadoOrderByEnviadoEmDesc(UUID usuarioId, ResultadoEnvio resultado);

    List<WhatsappEnvio> findByUsuarioIdOrderByEnviadoEmDesc(UUID usuarioId, Pageable pageable);
}
