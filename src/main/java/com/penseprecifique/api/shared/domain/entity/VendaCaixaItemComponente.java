package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** V0.13.0 (#516, RN-NOVA-9) — snapshot dos componentes de um Item de Catálogo (RN-NOVA-1) no
 * momento em que o item entrou na venda de Caixa, mesmo espírito de
 * {@link com.penseprecifique.api.shared.domain.entity.OrcamentoItemComponente} (Orçamento). Item
 * direto (Produto sem Catálogo) não tem linha aqui. Nunca exposta em resposta de API: sem preço
 * próprio, componente de catálogo não é vendido separado desde RN-NOVA-2/3 — não confundir com
 * {@link VendaCaixaItemCustomizacao} (customização ad-hoc, RN-030, sempre Produto, com preço). */
@Entity
@Table(name = "venda_caixa_item_componentes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendaCaixaItemComponente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_caixa_item_id", nullable = false)
    private VendaCaixaItem vendaCaixaItem;

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
