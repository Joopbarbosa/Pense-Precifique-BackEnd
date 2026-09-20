package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "itens_catalogo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogo_id", nullable = false)
    private Catalogo catalogo;

    @Column(nullable = false)
    private String nome;

    /** Tempo de producao do item inteiro, mesmo conceito de Produto.tempoProducao — RN-NOVA-2 */
    @Column(name = "tempo_producao", nullable = false)
    @Builder.Default
    private Integer tempoProducao = 0;

    /** RN-NOVA-3 — margem propria do item, mesmo modelo calculado+override de Produto (PDT-005). */
    @Column(name = "margem_lucro", precision = 5, scale = 2)
    private BigDecimal margemLucro;

    @Column(name = "preco_venda", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "override", nullable = false)
    @Builder.Default
    private Boolean override = false;

    /** RN-NOVA-6 — URL pública do objeto no R2, nunca o binário. Opcional. */
    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    /** RN-NOVA-7 — mesmo texto exibido depois no PDF do catálogo (#519). Máx. 150 caracteres. */
    @Column(name = "descricao", length = 150)
    private String descricao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
