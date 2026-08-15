package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoDocumentoReciboSinalPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String metodoRecebido;
    private String valorRecebido;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
}
