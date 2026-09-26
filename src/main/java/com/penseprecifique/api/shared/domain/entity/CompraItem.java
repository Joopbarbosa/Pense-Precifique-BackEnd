package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #541 (V0.15.0, DT-NOVA-3) — linha da compra. Quantidade e preço total podem faltar no rascunho;
 * a confirmação exige os dois. {@code precoUnitarioPago} e custo anterior/posterior são gravados na
 * confirmação: o preço pago (preço total ÷ quantidade) é separado do custo médio do insumo e é o
 * dado dos gráficos (RN-NOVA-15); o custo anterior/posterior é a base do cancelamento (RN-NOVA-9)
 * e do modal de impacto (RN-NOVA-8).
 */
@Entity
@Table(name = "compra_itens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Cliente fornecedor;

    @Column(precision = 15, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "preco_total", precision = 15, scale = 2)
    private BigDecimal precoTotal;

    @Column(name = "preco_unitario_pago", precision = 15, scale = 4)
    private BigDecimal precoUnitarioPago;

    @Column(name = "custo_unitario_anterior", precision = 15, scale = 4)
    private BigDecimal custoUnitarioAnterior;

    @Column(name = "custo_unitario_posterior", precision = 15, scale = 4)
    private BigDecimal custoUnitarioPosterior;

    @Column(nullable = false)
    private Integer ordem;
}
