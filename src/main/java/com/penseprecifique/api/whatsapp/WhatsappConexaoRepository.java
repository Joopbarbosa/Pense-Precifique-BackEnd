package com.penseprecifique.api.whatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WhatsappConexaoRepository extends JpaRepository<WhatsappConexao, UUID> {

    Optional<WhatsappConexao> findByUsuarioId(UUID usuarioId);

    /** DT-NOVA-14 — trava por usuária: serializa conferência de limite e gravação do envio. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM WhatsappConexao c WHERE c.usuarioId = :usuarioId")
    Optional<WhatsappConexao> travarPorUsuario(@Param("usuarioId") UUID usuarioId);
}
