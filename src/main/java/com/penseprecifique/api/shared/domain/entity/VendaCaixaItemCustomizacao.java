package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #487 — customização anexada a um {@link VendaCaixaItem} (fixa do Catálogo, expandida
 * automaticamente, ou ad-hoc escolhida na venda) — mesmo padrão de {@code OrcamentoItemCustomizacao}
 * (reabertura de RN-NOVA-1, ver modulos/CAIXA/decisoes-caixa.md).
 */
@Entity
@Table(name = "venda_caixa_item_customizacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendaCaixaItemCustomizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_caixa_item_id", nullable = false)
    private VendaCaixaItem vendaCaixaItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantidade = 1;

    @Column(name = "preco_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    /** #575/RN-NOVA-26 (V0.15.0) — custo de material por unidade no momento da venda (sem mão de obra).
     *  Nulo = venda anterior à V0.15.0 (CMV estimado, RN-NOVA-27). */
    @Column(name = "custo_material_unitario", precision = 15, scale = 4)
    private BigDecimal custoMaterialUnitario;
}
