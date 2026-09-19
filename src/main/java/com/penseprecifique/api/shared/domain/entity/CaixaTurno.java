package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #488 — turno de caixa (Caixa/PDV, Epic #416). Só 1 `ABERTO` por usuária por vez (RN-NOVA-6,
 * reforçado por índice único parcial na migration). `VendaCaixa` (#487) exige um turno `ABERTO`
 * para existir (RN-NOVA-5) — FK real, não referência solta (DT-NOVA-2).
 */
@Entity
@Table(name = "caixa_turnos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaixaTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_abertura", nullable = false)
    private LocalDateTime dataAbertura;

    @Column(name = "valor_abertura", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAbertura;

    /** null enquanto o turno está ABERTO — é o que define a janela de cancelamento de VendaCaixa (RN-NOVA-4). */
    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    /** RN-NOVA-9 — valorAbertura + suprimentos - sangrias + vendas em DINHEIRO do turno. Só existe após o fechamento. */
    @Column(name = "valor_fechamento_esperado", precision = 15, scale = 2)
    private BigDecimal valorFechamentoEsperado;

    @Column(name = "valor_fechamento_informado", precision = 15, scale = 2)
    private BigDecimal valorFechamentoInformado;

    /** valorFechamentoInformado - valorFechamentoEsperado — nunca bloqueia o fechamento (RN-NOVA-9). */
    @Column(precision = 15, scale = 2)
    private BigDecimal diferenca;

    /** #488 (V0.12.0) — obrigatória (mín. 30 caracteres) só quando houve diferença no fechamento;
     * validada no Service por ser regra condicional. */
    @Column(name = "fechamento_justificativa", columnDefinition = "TEXT")
    private String fechamentoJustificativa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCaixaTurno status;
}
