package com.penseprecifique.api.domain.entity;

import com.penseprecifique.api.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoProduto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimentacoes_produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoProduto tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoMovimentacaoProduto motivo;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "referencia_tipo")
    private String referenciaTipo;

    // RN-050: snapshot do catálogo/preço no momento da venda (movimentação ORCAMENTO)
    @Column(name = "catalogo_referencia")
    private String catalogoReferencia;

    @Column(name = "preco_vendido", precision = 10, scale = 2)
    private BigDecimal precoVendido;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estornada = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
