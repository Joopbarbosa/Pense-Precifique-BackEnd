package com.penseprecifique.api.dto.response;

import com.penseprecifique.api.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ProdutoRelacionadoResponse {

    private UUID id;
    private String nome;
    private TipoProduto tipo;
}
