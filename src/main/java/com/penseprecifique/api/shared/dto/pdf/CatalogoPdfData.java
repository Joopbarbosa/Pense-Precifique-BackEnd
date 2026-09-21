package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CatalogoPdfData {
    private String numeroFormatado;
    private String nomeEmpresa;
    private String emailEmpresa;
    private String telefoneEmpresa;
    private String logoUrlEmpresa;
    private String nomeCatalogo;
    private List<ItemCatalogoPdfData> itens;
}
