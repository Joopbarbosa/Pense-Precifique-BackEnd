package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ProducaoProdutoResponse {

    private UUID produtoId;
    private String nomeProduto;
    private TipoProduto tipoProduto;
    private BigDecimal quantidade;
}
