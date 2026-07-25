package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.TipoExibicaoQuantidade;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #141 — unicidade real de nome+marca por usuário é um índice parcial com COALESCE, não representável
 * em {@code @UniqueConstraint} (que não suporta COALESCE nem WHERE): {@code CREATE UNIQUE INDEX
 * idx_insumos_nome_marca_usuario ON insumos (usuario_id, nome, COALESCE(marca, '')) WHERE deleted_at
 * IS NULL} (migration V1). Sem efeito em runtime de qualquer forma — {@code ddl-auto: validate} nunca
 * gera schema a partir de anotação JPA; a constraint real é a da migration.
 */
@Entity
@Table(name = "insumos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private String nome;

    @Column
    private String marca;

    @Column(name = "unidade_medida", nullable = false)
    private String unidadeMedida;

    @Column(name = "custo_unitario", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal custoUnitario = BigDecimal.ZERO;

    @Column(name = "estoque_atual", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal estoqueAtual = BigDecimal.ZERO;

    @Column(name = "estoque_minimo", precision = 15, scale = 4)
    private BigDecimal estoqueMinimo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fracionavel = true;

    // RN-NOVA-1 — só tem sentido quando fracionavel = true; null quando fracionavel = false.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_exibicao_quantidade")
    private TipoExibicaoQuantidade tipoExibicaoQuantidade;

    @Column(name = "permitir_estoque_negativo", nullable = false)
    @Builder.Default
    private Boolean permitirEstoqueNegativo = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
