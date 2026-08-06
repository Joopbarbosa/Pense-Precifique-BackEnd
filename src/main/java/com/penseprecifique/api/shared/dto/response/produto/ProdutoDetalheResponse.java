package com.penseprecifique.api.shared.dto.response.produto;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProdutoDetalheResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private String nome;
    private TipoProduto tipo;
    private String descricao;
    private Integer tempoProducao;
    private BigDecimal precoVenda;
    private BigDecimal precoCusto;
    private BigDecimal margemLucro;
    private boolean override;
    /** Calculado no Service — RN-038a, nunca persistido. Pendente de P-005 (RN-039/custoUnitario). */
    private BigDecimal precoSugerido;
    private BigDecimal rendimento;
    /** Custo Total do lote — calculado no Service (RN-039), nunca persistido. Pendente de P-005. */
    private BigDecimal custoTotalLote;
    /** Custo Unitário (Custo Total ÷ Rendimento) — hoje espelha precoCusto ate P-005 implementar a divisao real (RN-039). */
    private BigDecimal custoUnitario;
    private BigDecimal estoqueAtual;
    private BigDecimal estoqueMinimo;
    private boolean permitirEstoqueNegativo;
    private boolean ativo;
    /** RN-051 — true se algum insumo direto da ficha técnica não for fracionável; decide o modo do campo de produção (quantidade livre vs. lotes). */
    private boolean algumInsumoNaoFracionavel;
    private List<FichaTecnicaItemResponse> fichaTecnica;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
