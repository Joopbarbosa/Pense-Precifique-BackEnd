package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/** #487 — 1 linha de pagamento de {@link VendaCaixa}; pagamento pode ser dividido entre vários
 * métodos (RN-NOVA-7). FK real para {@link MetodoPagamentoConfiguravel} — não referência solta,
 * destino sempre único (DT-NOVA-2). */
@Entity
@Table(name = "venda_caixa_pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendaCaixaPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_caixa_id", nullable = false)
    private VendaCaixa vendaCaixa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metodo_pagamento_id", nullable = false)
    private MetodoPagamentoConfiguravel metodoPagamento;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;
}
