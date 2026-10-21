package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.Produto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #672/RN-NOVA-10, DT-NOVA-6 (V0.17.0) — uma mudança do preço sugerido de um produto: valor anterior e novo (2 casas, a
 * mesma conta do detalhe do produto), causa e referência do evento (ex.: COM-12). Nunca guarda nem altera o preço de venda.
 */
@Entity
@Table(name = "mudancas_preco_sugerido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MudancaPrecoSugerido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "valor_anterior", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAnterior;

    @Column(name = "valor_novo", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorNovo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CausaPrecoSugerido causa;

    /** Id do evento (compra), quando existe. */
    @Column(name = "referencia_id")
    private UUID referenciaId;

    /** Texto legível do evento: COM-12, PRO-3, INS-4 (RN-053); nulo para o valor da hora. */
    @Column(name = "referencia_texto", length = 40)
    private String referenciaTexto;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
