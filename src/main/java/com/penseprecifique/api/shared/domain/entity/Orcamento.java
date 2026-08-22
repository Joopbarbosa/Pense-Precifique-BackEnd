package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.converter.TipoDescontoConverter;
import com.penseprecifique.api.shared.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orcamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "numero", updatable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private StatusOrcamento status = StatusOrcamento.RASCUNHO;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", nullable = false, length = 20)
    @Builder.Default
    private MetodoPagamento metodoPagamento = MetodoPagamento.PIX;

    @Column(name = "metodo_pagamento_obs", columnDefinition = "TEXT")
    private String metodoPagamentoObs;

    @Column(name = "prazo_producao_dias")
    private Integer prazoProducaoDias;

    @Column(name = "inicio_assim_que_aprovado", nullable = false)
    @Builder.Default
    private Boolean inicioAssimQueAprovado = true;

    @Column(name = "data_inicio_estimada")
    private LocalDate dataInicioEstimada;

    @Column(name = "data_aprovacao")
    private LocalDateTime dataAprovacao;

    @Column(name = "sinal_ativo", nullable = false)
    @Builder.Default
    private Boolean sinalAtivo = false;

    @Column(name = "percentual_sinal", precision = 5, scale = 2)
    private BigDecimal percentualSinal;

    @Column(name = "valor_sinal", precision = 10, scale = 2)
    private BigDecimal valorSinal;

    @Column(name = "data_sinal_pago")
    private LocalDateTime dataSinalPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_sinal_recebido", length = 20)
    private MetodoPagamento metodoSinalRecebido;

    @Column(name = "metodo_sinal_recebido_obs", columnDefinition = "TEXT")
    private String metodoSinalRecebidoObs;

    @Column(name = "cancelamento_motivo", columnDefinition = "TEXT")
    private String cancelamentoMotivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelamento_tipo", length = 20)
    private TipoCancelamento cancelamentoTipo;

    @Column(name = "percentual_multa", precision = 5, scale = 2)
    private BigDecimal percentualMulta;

    @Column(name = "valor_multa", precision = 10, scale = 2)
    private BigDecimal valorMulta;

    @Column(name = "valor_devolvido_multa", precision = 10, scale = 2)
    private BigDecimal valorDevolvidoMulta;

    @Column(name = "estorno_sinal")
    private Boolean estornoSinal;

    @Column(name = "data_estorno_sinal")
    private LocalDateTime dataEstornoSinal;

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Convert(converter = TipoDescontoConverter.class)
    @Column(name = "desconto_tipo", length = 5)
    @Builder.Default
    private TipoDesconto descontoTipo = TipoDesconto.PERCENTUAL;

    @Column(name = "desconto_valor", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "data_validade")
    private LocalDateTime dataValidade;

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
