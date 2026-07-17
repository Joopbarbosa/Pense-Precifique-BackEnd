package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemPdfData {
    private String nomeProduto;
    private String customizacoes;
    private String quantidade;
    private String precoUnitario;
    private String subtotal;
}
