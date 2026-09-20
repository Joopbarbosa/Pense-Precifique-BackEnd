package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

/**
 * Corpo enviado a {@code POST /render/catalogo/{id}?format=pdf} no microsserviço
 * pense-precifique-pdf — espelha {@code catalogoSchema} (contrato-pdf.md).
 */
@Data
@Builder
public class PdfMicroservicoCatalogoPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoCatalogoPayload documento;
}
