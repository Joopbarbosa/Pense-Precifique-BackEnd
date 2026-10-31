package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** V0.19.0 (#1172, DT-NOVA-1, RN-NOVA-2/3) — parcela de um lançamento. Só o {@link LancamentoFinanceiroService} a escreve. */
@Entity
@Table(name = "parcelas_financeiras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lancamento_id", nullable = false, updatable = false)
    private Lancamento lancamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SituacaoParcela situacao = SituacaoParcela.EM_ABERTO;

    @Column(name = "data_baixa")
    private LocalDate dataBaixa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metodo_pagamento_id")
    private MetodoPagamentoConfiguravel metodoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_baixa", length = 10)
    private OrigemBaixa origemBaixa;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (createdAt == null) createdAt = agora;
        if (updatedAt == null) updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
