package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** #546/RN-NOVA-12 (V0.15.0, DT-NOVA-8) — lista de compras gerada (LST-N), retrato imutável. */
@Entity
@Table(name = "listas_compra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListaCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "gerada_em", nullable = false)
    private LocalDateTime geradaEm;

    @PrePersist
    void prePersist() {
        if (geradaEm == null) geradaEm = LocalDateTime.now();
    }
}
