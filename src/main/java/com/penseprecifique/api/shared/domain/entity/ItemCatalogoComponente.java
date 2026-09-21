package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RN-NOVA-1 (V0.13.0, #516) — componente de um Item de Catalogo: Insumo XOR Produto/Customizacao
 * (produtoBase, mesmo nome ja usado por FichaTecnicaItem), com quantidade propria. Substitui a FK
 * direta ItemCatalogo.produto e a antiga tabela ItemCatalogoCustomizacao.
 */
@Entity
@Table(name = "item_catalogo_componentes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemCatalogoComponente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_catalogo_id", nullable = false)
    private ItemCatalogo itemCatalogo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id")
    private Insumo insumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_base_id")
    private Produto produtoBase;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
