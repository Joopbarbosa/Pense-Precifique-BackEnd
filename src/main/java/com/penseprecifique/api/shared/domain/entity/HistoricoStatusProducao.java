package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "historico_status_producao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricoStatusProducao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producao_id", nullable = false)
    private Producao producao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior")
    private EstadoProducao statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false)
    private EstadoProducao statusNovo;

    @Column(name = "data_transicao", nullable = false)
    private LocalDateTime dataTransicao;

    @Column(name = "justificativa", columnDefinition = "TEXT")
    private String justificativa;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false)
    private OrigemHistoricoStatus origem;

    @PrePersist
    void prePersist() {
        if (dataTransicao == null) dataTransicao = LocalDateTime.now();
    }
}
