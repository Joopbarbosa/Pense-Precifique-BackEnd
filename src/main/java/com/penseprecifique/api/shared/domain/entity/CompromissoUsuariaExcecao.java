package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.VinculoSituacao;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * V0.18.0 (#1039, DT-NOVA-5) — a ocorrência que fugiu do padrão da série: concluída, cancelada ou movida/editada só nela.
 * Chave: compromisso + data original. Os campos próprios (título, horário, vínculos) só valem quando preenchidos; concluir
 * e cancelar gravam os valores vigentes, para a ocorrência manter o que tinha quando a série inteira for editada depois.
 */
@Entity
@Table(name = "compromissos_usuaria_excecoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompromissoUsuariaExcecao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compromisso_id", nullable = false, updatable = false)
    private CompromissoUsuaria compromisso;

    @Column(name = "data_original", nullable = false, updatable = false)
    private LocalDate dataOriginal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SituacaoCompromisso situacao = SituacaoCompromisso.PENDENTE;

    @Column(name = "nova_data")
    private LocalDate novaData;

    @Column(length = 120)
    private String titulo;

    private LocalTime horario;

    /** true quando o horário (inclusive "sem horário") é próprio da ocorrência; false herda o do mestre. */
    @Column(name = "horario_proprio", nullable = false)
    @Builder.Default
    private boolean horarioProprio = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id")
    private Orcamento orcamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "orcamento_situacao", nullable = false, length = 10)
    @Builder.Default
    private VinculoSituacao orcamentoSituacao = VinculoSituacao.HERDA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id")
    private Insumo insumo;

    @Enumerated(EnumType.STRING)
    @Column(name = "insumo_situacao", nullable = false, length = 10)
    @Builder.Default
    private VinculoSituacao insumoSituacao = VinculoSituacao.HERDA;

    @Column(name = "observacao_cancelamento", length = 500)
    private String observacaoCancelamento;

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
