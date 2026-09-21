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
    private String nome;
    private List<ItemCatalogoComponenteResponse> componentes = new ArrayList<>();
    private Integer tempoProducao;
    private BigDecimal margemLucro;
    /** Calculado no Service (RN-NOVA-2) — soma dos componentes + mão de obra, nunca persistido. */
    private BigDecimal custoTotal;
    private BigDecimal precoVenda;
    /** Calculado no Service (RN-NOVA-3) — nunca persistido. */
    private BigDecimal precoSugerido;
    private boolean override;
    /** RN-NOVA-4 — true quando qualquer componente está inativo/excluído; item permanece mas fica
     * bloqueado para venda. */
    private boolean bloqueadoParaVenda;
    /** RN-NOVA-6 — URL pública do objeto no R2; null quando o item não tem foto. */
    private String fotoUrl;
    /** RN-NOVA-7 — também exibida no PDF do catálogo (#519). */
    private String descricao;
}
