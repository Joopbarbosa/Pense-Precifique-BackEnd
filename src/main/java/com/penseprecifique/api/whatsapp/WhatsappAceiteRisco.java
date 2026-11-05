package com.penseprecifique.api.whatsapp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_aceites_risco")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappAceiteRisco {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "aceito_em", nullable = false)
    private Instant aceitoEm;

    @Column(name = "versao_texto", nullable = false, length = 20)
    private String versaoTexto;
}
