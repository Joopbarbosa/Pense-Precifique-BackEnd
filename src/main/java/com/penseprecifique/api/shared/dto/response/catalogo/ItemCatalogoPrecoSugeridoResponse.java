package com.penseprecifique.api.shared.dto.response.catalogo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class ItemCatalogoPrecoSugeridoResponse {

    /** RN-NOVA-2 — soma dos custos dos componentes (sem mão de obra). */
    private BigDecimal custoComponentes;
    /** RN-NOVA-2 — mão de obra do próprio item (tempoProducao/60 × valorHora). */
    private BigDecimal custoMaoDeObra;
    /** custoComponentes + custoMaoDeObra. */
    private BigDecimal custoTotal;
    /** RN-NOVA-3 — custoTotal × (1 + margemLucro/100). */
    private BigDecimal precoSugerido;
}
