package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/** #487 — item de {@link VendaCaixa}. Sempre `Produto` direto, nunca `ItemCatalogo` (RN-NOVA-1). */
@Entity
@Table(name = "venda_caixa_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendaCaixaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_caixa_id", nullable = false)
    private VendaCaixa vendaCaixa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    /** Snapshot de Produto.precoVenda no momento da venda (RN-NOVA-3, mesmo princípio de ORC-001). */
    @Column(name = "preco_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;
}
