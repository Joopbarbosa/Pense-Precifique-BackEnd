package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V0.19.0 (#1172, DT-NOVA-1) — lançamento a pagar ou a receber. O total é sempre a soma das parcelas não canceladas e só
 * o {@link LancamentoFinanceiroService} o altera. Cada usuária só enxerga os seus (DT-NOVA-16).
 */
@Entity
@Table(name = "lancamentos_financeiros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TipoLancamento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SituacaoLancamento situacao = SituacaoLancamento.ATIVO;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    /** V0.19.0 (#1174, RN-NOVA-12) — ESTORNO_SINAL marca a saída do estorno; nulo nos demais. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private SubtipoLancamento subtipo;

    @Column(name = "fora_projecao", nullable = false)
    @Builder.Default
    private boolean foraProjecao = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private OrigemLancamento origem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id", updatable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id", updatable = false)
    private Orcamento orcamento;

    /** F6 (#1177) — só despesa (A_PAGAR) tem categoria; receita fica sem. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", updatable = false)
    private CategoriaFinanceira categoria;

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
