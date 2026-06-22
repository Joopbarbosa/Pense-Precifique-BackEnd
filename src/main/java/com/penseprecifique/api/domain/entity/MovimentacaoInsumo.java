package com.penseprecifique.api.domain.entity;

import com.penseprecifique.api.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoInsumo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimentacoes_insumo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoInsumo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoMovimentacaoInsumo motivo;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "referencia_tipo")
    private ReferenciaMovimentacaoTipo referenciaTipo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estornada = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
