package com.penseprecifique.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoItemResponse {

    private UUID id;
    /** RN-054 — preenchido apenas quando a origem do item é o Catálogo. */
    private UUID itemCatalogoId;
    private UUID produtoId;
    private String nomeProduto;
    /** RN-054 — preenchido apenas quando a origem do item é produto avulso (sem Catálogo). */
    private BigDecimal margemAplicada;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
    private List<OrcamentoItemCustomizacaoResponse> customizacoes;
}
