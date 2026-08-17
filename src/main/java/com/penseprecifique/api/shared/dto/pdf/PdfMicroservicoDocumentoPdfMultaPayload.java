package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PdfMicroservicoDocumentoPdfMultaPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String telefoneCliente;
    private String emailCliente;
    private String motivo;
    private String percentualMulta;
    private String valorMulta;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
    private String dataCancelamento;
    private List<PdfMicroservicoItemPayload> itens;
}
