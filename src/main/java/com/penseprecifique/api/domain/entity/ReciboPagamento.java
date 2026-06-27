package com.penseprecifique.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recibos_pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReciboPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orcamento_id", nullable = false, unique = true)
    private Orcamento orcamento;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDateTime dataPagamento;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_sinal_pago", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorSinalPago = BigDecimal.ZERO;

    @Column(name = "valor_restante_pago", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorRestantePago;

    @Column(name = "total_quitado", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalQuitado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (dataPagamento == null) dataPagamento = now;
        if (createdAt == null) createdAt = now;
    }
}
