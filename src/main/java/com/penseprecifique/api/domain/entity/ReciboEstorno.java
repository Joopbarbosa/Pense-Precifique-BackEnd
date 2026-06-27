package com.penseprecifique.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recibos_estorno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReciboEstorno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orcamento_id", nullable = false, unique = true)
    private Orcamento orcamento;

    @Column(name = "data_estorno", nullable = false)
    private LocalDateTime dataEstorno;

    @Column(name = "valor_estornado", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorEstornado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
