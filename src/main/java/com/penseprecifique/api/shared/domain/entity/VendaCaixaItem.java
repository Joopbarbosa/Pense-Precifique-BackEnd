package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #487 — item de {@link VendaCaixa}. Origem XOR {@code itemCatalogo}/{@code produto} (RN-NOVA-1
 * reaberta no teste manual do V0.12.0 — mesmo padrão de {@code OrcamentoItem}, ver
 * modulos/CAIXA/decisoes-caixa.md).
 */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_catalogo_id")
    private ItemCatalogo itemCatalogo;

    /** Origem alternativa ao Catálogo: produto vendido direto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    /** Snapshot do preço de venda no momento da venda (RN-NOVA-3, mesmo princípio de ORC-001). */
    @Column(name = "preco_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    /** Produto vendido, seja via Catálogo (item_catalogo) ou direto (produto) — mesmo helper de
     *  {@code OrcamentoItem#getProdutoVendido()}. */
    public Produto getProdutoVendido() {
        return itemCatalogo != null ? itemCatalogo.getProduto() : produto;
    }
}
