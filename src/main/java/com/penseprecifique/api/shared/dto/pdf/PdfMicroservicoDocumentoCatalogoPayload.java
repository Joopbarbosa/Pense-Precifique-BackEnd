package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PdfMicroservicoDocumentoCatalogoPayload {
    private String numeroFormatado;
    private String nome;
    private List<PdfMicroservicoItemCatalogoPayload> itens;
}
