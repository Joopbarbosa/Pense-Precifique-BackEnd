package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #546 (V0.15.0, DT-NOVA-8) — linha da lista gerada. Nome, unidade, estoques, fornecedor e preço
 * são CÓPIAS do momento da geração: o retrato e o PDF (#547) nunca leem os dados atuais. As FKs de
 * insumo/fornecedor servem só para "criar compra a partir da lista" (RN-NOVA-13).
 */
@Entity
@Table(name = "lista_compra_itens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListaCompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_id", nullable = false)
    private ListaCompra lista;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(name = "insumo_nome", nullable = false)
    private String insumoNome;

    @Column(length = 50)
    private String unidade;

    @Column(name = "estoque_atual", nullable = false, precision = 15, scale = 4)
    private BigDecimal estoqueAtual;

    @Column(name = "estoque_minimo", precision = 15, scale = 4)
    private BigDecimal estoqueMinimo;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Cliente fornecedor;

    @Column(name = "fornecedor_nome")
    private String fornecedorNome;

    @Column(name = "preco_referencia", precision = 15, scale = 4)
    private BigDecimal precoReferencia;

    @Column(nullable = false)
    private Integer ordem;
}
