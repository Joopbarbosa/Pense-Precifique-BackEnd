package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemCatalogoPdfData {
    private String nome;
    private String descricao;
    private String fotoUrl;
}
