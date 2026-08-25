package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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
    @Column(name = "status_novo")
    private EstadoProducao statusNovo;

    @Column(name = "data_transicao", nullable = false)
    private LocalDateTime dataTransicao;

    @Column(name = "justificativa", columnDefinition = "TEXT")
    private String justificativa;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false)
    private OrigemHistoricoStatus origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false)
    @Builder.Default
    private TipoEventoHistoricoProducao tipoEvento = TipoEventoHistoricoProducao.STATUS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(name = "quantidade")
    private BigDecimal quantidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referencia_orcamento_id")
    private Orcamento referenciaOrcamento;

    @PrePersist
    void prePersist() {
        if (dataTransicao == null) dataTransicao = LocalDateTime.now();
    }
}
