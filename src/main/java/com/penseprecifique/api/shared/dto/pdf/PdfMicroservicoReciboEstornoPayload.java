package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoReciboEstornoPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoReciboEstornoPayload documento;
}
