package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

/**
 * Corpo enviado a {@code POST /render/orcamento/{id}?format=pdf} no microsserviço
 * pense-precifique-pdf — espelha {@code orcamentoSchema} (contrato-pdf.md seção 1).
 */
@Data
@Builder
public class PdfMicroservicoOrcamentoPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoOrcamentoPayload documento;
}
