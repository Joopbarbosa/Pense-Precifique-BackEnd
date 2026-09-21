package com.penseprecifique.api.shared.dto.response.catalogo;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** RN-NOVA-1 (V0.13.0, #516) — mesmo formato de {@code FichaTecnicaItemResponse}. */
@Getter
@Setter
public class ItemCatalogoComponenteResponse {

    private UUID id;
    private UUID insumoId;
    private String nomeInsumo;
    /** OpenProject #528 — mesmo padrão de {@code FichaTecnicaItemResponse#fracionavelInsumo}: só
     *  preenchido quando o componente é Insumo; {@code null} quando é Produto-base. */
    private Boolean fracionavelInsumo;
    private UUID produtoBaseId;
    private String nomeProdutoBase;
    private TipoProduto tipoProdutoBase;
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
    private BigDecimal custoTotal;
    /** RN-NOVA-4 — false quando o Produto/Insumo deste componente está inativo ou excluído. */
    private boolean ativo;
}
