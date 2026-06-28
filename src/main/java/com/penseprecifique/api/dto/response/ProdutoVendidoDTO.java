package com.penseprecifique.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProdutoVendidoDTO {
    private String nomeProduto;
    private Long quantidade;
}
