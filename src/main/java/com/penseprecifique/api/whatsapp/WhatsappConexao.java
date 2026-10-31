package com.penseprecifique.api.whatsapp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_conexoes")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappConexao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private UUID usuarioId;

    @Column(nullable = false, length = 60)
    private String instancia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoWhatsapp estado = EstadoWhatsapp.NAO_CONECTADO;

    @Column(name = "estado_alterado_em", nullable = false)
    private Instant estadoAlteradoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
