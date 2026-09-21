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
    /** #531 (DT-NOVA-5) — exibida na aba Dados Básicos, editável via POST/DELETE /produtos/{id}/foto. */
    private String fotoUrl;
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
    /** DT-NOVA-1 (V0.14.0, #294) — soma do custo dos componentes da ficha técnica, calculado ao vivo (nunca persistido), mesma fonte que já compõe custoTotalLote. */
    private BigDecimal precoInsumo;
    /** DT-NOVA-1 (V0.14.0, #294) — calcularCustoMaoDeObra(tempoProducao, valorHora), calculado ao vivo, mesma fonte que já compõe custoTotalLote. */
    private BigDecimal precoMaoDeObra;
    /** DT-NOVA-1 (V0.14.0, #294) — precoVenda − custoUnitario (lucro por unidade, valor absoluto), calculado ao vivo. */
    private BigDecimal precoLucro;
    private BigDecimal estoqueAtual;
    private BigDecimal estoqueMinimo;
    private boolean permitirEstoqueNegativo;
    private boolean ativo;
    /** RN-051 — true se algum insumo direto da ficha técnica não for fracionável; decide o modo do campo de produção (quantidade livre vs. lotes). Fonte: !Produto.fracionavel (RN-NOVA-2). */
    private boolean algumInsumoNaoFracionavel;
    /** RN-NOVA-2 (V0.10.0, #299) — valor atual exibido/editável (persistido, calculado+override). */
    private Boolean fracionavel;
    /** true quando a artesã já editou fracionavel manualmente. */
    private Boolean fracionavelOverride;
    private List<FichaTecnicaItemResponse> fichaTecnica;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
