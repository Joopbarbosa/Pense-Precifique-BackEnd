package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.RecorrenciaCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * V0.18.0 (#1039, DT-NOVA-5) — compromisso criado pela usuária: o mestre da série. As ocorrências não são gravadas; só as
 * que fugiram do padrão viram {@link CompromissoUsuariaExcecao}. {@code situacao} do mestre só é CANCELADO quando a série
 * inteira foi cancelada (cancela as ocorrências que não têm exceção própria).
 */
@Entity
@Table(name = "compromissos_usuaria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompromissoUsuaria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    private LocalTime horario;

    /** Hora de término opcional (#1063); exige horário e é posterior a ele. */
    @Column(name = "horario_fim")
    private LocalTime horarioFim;

    /** Descrição opcional (#1159), até 500 caracteres; vale para a série inteira. */
    @Column(length = 500)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id")
    private Orcamento orcamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id")
    private Insumo insumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private RecorrenciaCompromisso recorrencia = RecorrenciaCompromisso.NENHUMA;

    /** Dias da semana da recorrência semanal, separados por vírgula (MONDAY,THURSDAY). */
    @Column(name = "dias_semana", length = 60)
    private String diasSemana;

    /** Vazia = série indefinida. */
    @Column(name = "quantidade_ocorrencias")
    private Integer quantidadeOcorrencias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SituacaoCompromisso situacao = SituacaoCompromisso.PENDENTE;

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
