package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #382 (RN-NOVA-44, DT-NOVA-10) — opção do orçamento Descritivo: um Produto OU um Item de catálogo, com o preço de
 * referência (gravado; nunca o preço atual) e a quantidade/unidade como texto livre opcional.
 */
@Entity
@Table(name = "orcamento_descritivo_opcoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrcamentoDescritivoOpcao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private Orcamento orcamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_catalogo_id")
    private ItemCatalogo itemCatalogo;

    @Column(name = "preco_referencia", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoReferencia;

    @Column(name = "quantidade_unidade", length = 500)
    private String quantidadeUnidade;

    @Column(name = "ordem", nullable = false)
    private Integer ordem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
