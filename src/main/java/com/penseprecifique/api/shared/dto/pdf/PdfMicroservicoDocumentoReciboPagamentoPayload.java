package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfMicroservicoDocumentoReciboPagamentoPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String metodoPagamento;
    private String valorTotal;
    private String valorSinalPago;
    private String valorRestantePago;
    private String totalQuitado;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
    private String dataPagamento;
}
