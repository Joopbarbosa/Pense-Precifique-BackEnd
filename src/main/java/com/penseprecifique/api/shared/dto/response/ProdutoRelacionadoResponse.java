package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ProdutoRelacionadoResponse {

    private UUID id;
    private String identificador;
    private String nome;
    private TipoProduto tipo;
}
