package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

/** Corpo de POST /render/compra/{id} — espelha compraSchema (#545). */
@Data
@Builder
public class PdfMicroservicoCompraPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoCompraPayload documento;
}
