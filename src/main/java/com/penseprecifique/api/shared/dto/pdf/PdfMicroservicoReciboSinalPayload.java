package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoReciboSinalPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoReciboSinalPayload documento;
}
