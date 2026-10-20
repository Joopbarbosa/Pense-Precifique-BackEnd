package com.penseprecifique.api.shared.dto.response.insumo;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ProdutoRelacionadoResponse {

    private UUID id;
    private String identificador;
    private String nome;
    /** #1018 (RN-NOVA-36): marca do produto, para a tela mostrar "Nome — Marca"; nula quando o cadastro não tem marca. */
    private String marca;
    private TipoProduto tipo;
    /** V0.17.0 #971 (DT-NOVA-18): quantidade do insumo na ficha e a unidade do insumo, para a modal ("2 m"). */
    private BigDecimal quantidade;
    private String unidade;
}
