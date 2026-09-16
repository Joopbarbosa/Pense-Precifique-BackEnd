package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoProduto tipo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    /** Tempo de producao do lote inteiro, nao da unidade individual — RN-038 */
    @Column(name = "tempo_producao", nullable = false)
    private Integer tempoProducao;

    @Column(name = "preco_venda", precision = 15, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "rendimento", precision = 10, scale = 4)
    private BigDecimal rendimento;

    /** RN-038a — modelo de preço unificado (PRODUTO e CUSTOMIZACAO) desde #210+231+234 */
    @Column(name = "margem_lucro", precision = 5, scale = 2)
    private BigDecimal margemLucro;

    /** true quando precoVenda foi editado manualmente — fica fixo, nao acompanha mudancas de margemLucro (RN-038a) */
    @Column(name = "override", nullable = false)
    @Builder.Default
    private Boolean override = false;

    @Column(name = "preco_custo", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal precoCusto = BigDecimal.ZERO;

    @Column(name = "estoque_atual", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal estoqueAtual = BigDecimal.ZERO;

    @Column(name = "estoque_minimo", precision = 15, scale = 4)
    private BigDecimal estoqueMinimo;

    @Column(name = "permitir_estoque_negativo", nullable = false)
    @Builder.Default
    private Boolean permitirEstoqueNegativo = true;

    /** RN-NOVA-2 (V0.10.0, #299, altera PDT-016) — valor atual exibido. Deriva ao vivo da ficha
     * técnica enquanto fracionavelOverride = false; fica congelado quando a artesã edita manualmente. */
    @Column(name = "fracionavel", nullable = false)
    @Builder.Default
    private Boolean fracionavel = true;

    /** true quando a artesã já editou fracionavel manualmente — trava o recálculo automático. */
    @Column(name = "fracionavel_override", nullable = false)
    @Builder.Default
    private Boolean fracionavelOverride = false;

    /**
     * #489 — campos fiscais mínimos (RN-NOVA-12/13, V0.12.0), todos opcionais: nenhum é exigido
     * para salvar/vender um produto (Orçamento ou Caixa) nesta versão. Preparação para emissão
     * futura de NFC-e/NF-e (fora de escopo) — sem cálculo de imposto, sem validação cruzada entre
     * eles. Beneficia igualmente Orçamento e Caixa (não é campo exclusivo de nenhum dos dois).
     */
    @Column(name = "codigo_barras")
    private String codigoBarras;

    @Column(name = "ncm")
    private String ncm;

    @Column(name = "cfop")
    private String cfop;

    @Column(name = "cest")
    private String cest;

    @Column(name = "unidade_comercial")
    private String unidadeComercial;

    /** RN-NOVA-13 — lista fechada de códigos válidos, validada em ProdutoService. */
    @Column(name = "csosn")
    private String csosn;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

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
