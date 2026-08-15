package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoDocumentoPdfMultaPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String motivo;
    private String percentualMulta;
    private String valorMulta;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
}
