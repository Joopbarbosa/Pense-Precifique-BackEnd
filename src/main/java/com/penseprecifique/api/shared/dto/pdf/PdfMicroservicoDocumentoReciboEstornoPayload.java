package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoDocumentoReciboEstornoPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String valorRecebido;
    private String dataEstorno;
}
