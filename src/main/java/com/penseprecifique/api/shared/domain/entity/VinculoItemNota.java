package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.OrigemVinculoItemNota;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V0.16.0 (#681, RN-NOVA-15, DT-NOVA-11) — memória da conciliação: o item {@code nomeItem} desta nota,
 * vindo do emitente {@code emitenteCnpj}, é o insumo {@code insumo} com o fator {@code fator}, ou é
 * ignorado. Único por usuária + CNPJ + nome normalizado.
 */
@Entity
@Table(name = "vinculos_item_nota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VinculoItemNota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "emitente_cnpj", nullable = false, length = 14)
    private String emitenteCnpj;

    @Column(name = "emitente_nome", length = 500)
    private String emitenteNome;

    @Column(name = "nome_item", nullable = false, length = 500)
    private String nomeItem;

    @Column(name = "nome_item_normalizado", nullable = false, length = 500)
    private String nomeItemNormalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id")
    private Insumo insumo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ignorar = false;

    @Column(precision = 15, scale = 4)
    private BigDecimal fator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemVinculoItemNota origem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
