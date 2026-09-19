package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #491 (V0.12.0) — taxa de uma parcela específica de um método de pagamento, usada só quando
 * {@link MetodoPagamentoConfiguravel#getTaxaParcelaUniforme()} é FALSE (taxa diferente por
 * parcela). Com taxa uniforme, a única taxa vigente é a {@code taxaMaquininha} do próprio método.
 */
@Entity
@Table(name = "metodo_pagamento_taxas_parcela")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetodoPagamentoTaxaParcela {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "metodo_pagamento_id", nullable = false)
    private UUID metodoPagamentoId;

    @Column(nullable = false)
    private Integer parcela;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxa;
}
