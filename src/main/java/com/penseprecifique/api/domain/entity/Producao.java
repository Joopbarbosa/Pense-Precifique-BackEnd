package com.penseprecifique.api.domain.entity;

import com.penseprecifique.api.domain.enums.StatusProducao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "producoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "numero", insertable = false, updatable = false)
    private Integer numero;

    @Column(name = "quantidade", nullable = false, precision = 10, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "data_producao", nullable = false)
    private LocalDateTime dataProducao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusProducao status = StatusProducao.ATIVA;

    @Column(name = "observacao_cancelamento", columnDefinition = "TEXT")
    private String observacaoCancelamento;

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (dataProducao == null) dataProducao = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
