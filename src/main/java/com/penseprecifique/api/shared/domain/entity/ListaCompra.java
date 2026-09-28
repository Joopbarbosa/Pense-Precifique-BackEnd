package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.StatusListaCompra;
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

    /** Nulo enquanto RASCUNHO (#596). */
    @Column(name = "gerada_em")
    private LocalDateTime geradaEm;

    // #596/RN-NOVA-41 (V0.15.0)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusListaCompra status = StatusListaCompra.GERADA;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (geradaEm == null && status != StatusListaCompra.RASCUNHO) geradaEm = createdAt;
    }
}
