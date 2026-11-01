package com.penseprecifique.api.whatsapp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Histórico de envio: número, tipo e id do documento, data, resultado. Nunca guarda o conteúdo. */
@Entity
@Table(name = "whatsapp_envios")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(length = 15)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 30)
    private TipoDocumentoWhatsapp tipoDocumento;

    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Column(name = "enviado_em", nullable = false)
    private Instant enviadoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResultadoEnvio resultado;

    @Column(length = 40)
    private String motivo;

    @Column(name = "consentimento_confirmado", nullable = false)
    private boolean consentimentoConfirmado;
}
