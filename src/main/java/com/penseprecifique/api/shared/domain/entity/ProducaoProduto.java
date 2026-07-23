package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "producao_produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProducaoProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producao_id", nullable = false)
    private Producao producao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "quantidade", nullable = false)
    private BigDecimal quantidade;

    /** #188/RN-NOVA-4 — perda declarada ao finalizar (Opção A: desconta do incremento de estoque, não é registro paralelo). */
    @Column(name = "quantidade_perdida", nullable = false)
    @Builder.Default
    private BigDecimal quantidadePerdida = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
