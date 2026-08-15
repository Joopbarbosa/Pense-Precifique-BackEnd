package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoEmpresaPayload {
    private String nome;
    private String email;
    private String whatsapp;
    private String logoUrl;
}
