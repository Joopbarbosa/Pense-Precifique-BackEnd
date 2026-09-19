package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #491 — método de pagamento configurável por usuária, consumido pelo pagamento dividido de
 * {@code VendaCaixa} (Caixa/PDV). 4 tipos fixos (semeados no registro, DT-NOVA-6) + tipo OUTRO de
 * nome livre. RN-NOVA-15/16/17.
 *
 * <p>Nome "Configuravel" no fim, e não apenas "MetodoPagamento", por colisão real de compilação:
 * já existe {@code shared.domain.enums.MetodoPagamento} (enum fixo usado só por
 * {@code Orcamento.metodoPagamento}), sem relação com esta entidade. Como esta classe vive no
 * mesmo package de {@code Orcamento} ({@code shared.domain.entity}), o mesmo nome simples vencia o
 * import wildcard do enum em {@code Orcamento.java}/{@code OrcamentoService.java}/
 * {@code PdfMapper.java}/{@code OrcamentoMapper.java} (regra do Java: tipo do mesmo package sempre
 * vence import-on-demand) — achado só durante a compilação real desta tarefa, não previsto pela
 * `estrutura` (DT-NOVA-4). Ver "Divergências de Premissa" em decisoes-config-perfil.md.
 */
@Entity
@Table(name = "metodos_pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetodoPagamentoConfiguravel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMetodoPagamento tipo;

    /** Só preenchido quando tipo = OUTRO — os 4 tipos fixos não têm nome próprio; o rótulo exibido
     * deriva do tipo no frontend (RN-NOVA-15). */
    @Column
    private String nome;

    /** Só aceito quando tipo IN (CARTAO_CREDITO, CARTAO_DEBITO), validado em Service — nullable na
     * mesma tabela, sem herança JPA (DT-NOVA-4). Informativo nesta versão, não desconta nada
     * (RN-NOVA-17). */
    @Column(name = "taxa_maquininha", precision = 5, scale = 2)
    private BigDecimal taxaMaquininha;

    /** #491 (V0.12.0) — parcelamento, só aceito em CARTAO_CREDITO (CHECK na V48; débito não
     * parcela). Nulo = método não parcela. */
    @Column(name = "max_parcelas")
    private Integer maxParcelas;

    /** TRUE: {@link #taxaMaquininha} vale para toda parcela. FALSE: a taxa vem de
     * {@link MetodoPagamentoTaxaParcela}, uma linha por parcela. */
    @Column(name = "taxa_parcela_uniforme")
    private Boolean taxaParcelaUniforme;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    /** Ordem de exibição no seletor de pagamento do Caixa — UX, opcional. */
    @Column
    private Integer ordem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** RN-NOVA-15 — não é coluna própria: verdadeiro se e somente se tipo = DINHEIRO. */
    @Transient
    public boolean isAfetaCaixaFisico() {
        return tipo == TipoMetodoPagamento.DINHEIRO;
    }
}
