package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.TipoCaixaMovimento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** #488 — sangria/suprimento de um {@link CaixaTurno}. RN-NOVA-8 (motivo mín. 30 caracteres,
 * mesmo padrão de PDT-009) validado no DTO de request, não aqui. */
@Entity
@Table(name = "caixa_movimentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaixaMovimento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_turno_id", nullable = false)
    private CaixaTurno caixaTurno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCaixaMovimento tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "data_movimento", nullable = false)
    private LocalDateTime dataMovimento;

    /** Quem registrou o movimento — rastreabilidade/antifraude (achado de pesquisa de mercado). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;
}
