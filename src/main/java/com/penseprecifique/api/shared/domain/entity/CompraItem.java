package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.TipoDesconto;

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

    /** Preço PAGO da linha (líquido de descontos). #576/RN-NOVA-28: calculado a partir do preço cheio. */
    @Column(name = "preco_total", precision = 15, scale = 2)
    private BigDecimal precoTotal;

    /** #576/RN-NOVA-28 (V0.15.0) — preço cheio digitado (antes de qualquer desconto). */
    @Column(name = "preco_cheio", precision = 15, scale = 2)
    private BigDecimal precoCheio;

    @Enumerated(EnumType.STRING)
    @Column(name = "desconto_tipo", length = 20)
    private TipoDesconto descontoTipo;

    /** Como foi digitado: R$ (VALOR) ou % (PERCENTUAL). */
    @Column(name = "desconto_informado", precision = 15, scale = 2)
    private BigDecimal descontoInformado;

    /** Desconto da própria linha em R$. */
    @Column(name = "desconto_linha", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal descontoLinha = BigDecimal.ZERO;

    /** Parte do desconto da nota rateada para esta linha, em R$. */
    @Column(name = "desconto_nota", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal descontoNota = BigDecimal.ZERO;

    @Column(name = "preco_unitario_pago", precision = 15, scale = 4)
    private BigDecimal precoUnitarioPago;

    @Column(name = "custo_unitario_anterior", precision = 15, scale = 4)
    private BigDecimal custoUnitarioAnterior;

    @Column(name = "custo_unitario_posterior", precision = 15, scale = 4)
    private BigDecimal custoUnitarioPosterior;

    @Column(nullable = false)
    private Integer ordem;
}
