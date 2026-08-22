package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RN-NOVA-6 (V0.8.2) — vínculo N:N entre Orçamento e Produção. Um orçamento pode se vincular a mais
 * de uma produção (cobre o caso de produção dividida via {@code ProducaoService.dividir()}) e, no
 * modelo, uma produção também poderia atender mais de um orçamento. Entidade de associação explícita
 * (sem precedente de {@code @ManyToMany} no projeto) — mesmo padrão de {@link OrcamentoItem}.
 */
@Entity
@Table(name = "orcamento_producoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrcamentoProducao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private Orcamento orcamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producao_id", nullable = false)
    private Producao producao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
