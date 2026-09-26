package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.OrigemCompra;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #541/RN-NOVA-4 (V0.15.0, DT-NOVA-3) — compra de insumos. COM-N (RN-053) nasce no primeiro salvar;
 * excluir rascunho é soft delete para o número nunca voltar à sequência. Itens em {@link CompraItem}.
 */
@Entity
@Table(name = "compras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusCompra status = StatusCompra.RASCUNHO;

    @Column(name = "data_compra", nullable = false)
    private LocalDate dataCompra;

    @Column(name = "multiplos_fornecedores", nullable = false)
    @Builder.Default
    private Boolean multiplosFornecedores = false;

    /** Fornecedor do cabeçalho (modo fornecedor único, ou default das linhas no modo múltiplo). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Cliente fornecedor;

    @Column(nullable = false)
    @Builder.Default
    private Boolean pago = false;

    /** #550/RN-NOVA-23 — obrigatório quando pago (CHECK chk_compra_pagamento). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metodo_pagamento_id")
    private MetodoPagamentoConfiguravel metodoPagamento;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrigemCompra origem = OrigemCompra.MANUAL;

    @Column(name = "confirmada_em")
    private LocalDateTime confirmadaEm;

    @Column(name = "cancelada_em")
    private LocalDateTime canceladaEm;

    @Column(name = "observacao_cancelamento", columnDefinition = "TEXT")
    private String observacaoCancelamento;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
}
