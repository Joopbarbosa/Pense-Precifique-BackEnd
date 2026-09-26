package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

/** Corpo de POST /render/lista-compras/{id} — espelha listaComprasSchema (#547). */
@Data
@Builder
public class PdfMicroservicoListaComprasPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private PdfMicroservicoDocumentoListaComprasPayload documento;
}
