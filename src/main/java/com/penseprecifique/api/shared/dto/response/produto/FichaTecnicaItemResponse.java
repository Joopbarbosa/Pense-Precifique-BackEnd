package com.penseprecifique.api.shared.dto.response.produto;

import com.penseprecifique.api.shared.domain.enums.TipoExibicaoQuantidade;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FichaTecnicaItemResponse {

    private UUID id;
    private UUID insumoId;
    private String nomeInsumo;
    private String marcaInsumo;
    private String unidadeMedida;
    private Boolean fracionavelInsumo;
    private TipoExibicaoQuantidade tipoExibicaoQuantidade;
    private UUID produtoBaseId;
    private String nomeProdutoBase;
    /** RN-NOVA-8 (V0.10.0, #462) — achado do teste manual: sem este campo, o Frontend não tem
     *  como distinguir Produto de Customização ao recarregar uma ficha técnica já salva (edição),
     *  e volta a rotular todo item com produtoBaseId como "produto". */
    private TipoProduto tipoProdutoBase;
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
    private BigDecimal custoTotal;
}
