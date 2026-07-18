package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.StatusProducao;
import com.penseprecifique.api.shared.domain.enums.TipoOrigemProducao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "producoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Nulo para produções do fluxo novo (N produtos via ProducaoProduto) — só preenchido
    // em produções legadas do fluxo antigo (1 produto, imediato).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(name = "numero", updatable = false)
    private Integer numero;

    @Column(name = "quantidade", precision = 10, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "data_producao", nullable = false)
    private LocalDateTime dataProducao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusProducao status = StatusProducao.ATIVA;

    @Column(name = "observacao_cancelamento", columnDefinition = "TEXT")
    private String observacaoCancelamento;

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoProducao estado = EstadoProducao.AGUARDANDO_INICIO;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_termino_prevista")
    private LocalDate dataTerminoPrevista;

    @Column(name = "data_termino_real")
    private LocalDate dataTerminoReal;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "justificativa_cancelamento", columnDefinition = "TEXT")
    private String justificativaCancelamento;

    @Column(name = "justificativa_nao_realizada", columnDefinition = "TEXT")
    private String justificativaNaoRealizada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producao_origem_id")
    private Producao producaoOrigem;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_origem")
    private TipoOrigemProducao tipoOrigem;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (dataProducao == null) dataProducao = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
