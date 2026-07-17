package com.penseprecifique.api.shared.dto.response;

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
    /** Identificador formatado (CTG-N) do catálogo, preenchido junto com itemCatalogoId. */
    private String catalogoIdentificador;
    /** Nome do catálogo, preenchido junto com itemCatalogoId. */
    private String catalogoNome;
    private UUID produtoId;
    private String nomeProduto;
    /** RN-054 — preenchido apenas quando a origem do item é produto avulso (sem Catálogo). */
    private BigDecimal margemAplicada;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
    private List<OrcamentoItemCustomizacaoResponse> customizacoes;
}
