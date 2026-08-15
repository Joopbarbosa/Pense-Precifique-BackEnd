package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoPdfMultaPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoPdfMultaPayload documento;
}
