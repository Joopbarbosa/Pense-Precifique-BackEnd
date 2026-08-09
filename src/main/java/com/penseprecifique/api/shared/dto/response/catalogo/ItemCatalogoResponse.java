package com.penseprecifique.api.shared.dto.response.catalogo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ItemCatalogoResponse {

    private UUID id;
    private UUID produtoId;
    private String produtoNome;
    private Integer quantidadePacote;
    private BigDecimal precoVenda;
    /** Calculado no Service (RN-042) — nunca persistido. Pendente de P-012. */
    private BigDecimal precoSugerido;
    private boolean override;
    /** RN-045 — true quando o Produto do item foi inativado/excluído; item permanece mas fica bloqueado para venda. */
    private boolean bloqueadoParaVenda;
    private List<CustomizacaoAnexadaResponse> customizacoesAnexadas = new ArrayList<>();
    /** #238 — tag global fracionável/estoque negativo/estoque atual, mesmo padrão de ProdutoResponse. */
    private boolean algumInsumoNaoFracionavel;
    private boolean permitirEstoqueNegativo;
    private BigDecimal estoqueAtual;
}
