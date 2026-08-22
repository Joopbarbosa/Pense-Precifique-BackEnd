package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PdfMicroservicoDocumentoReciboEstornoPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String telefoneCliente;
    private String emailCliente;
    private String valorRecebido;
    private String dataEstorno;
    private String dataEmissao;
    private String dataAprovacao;
    private String motivo;
    private List<PdfMicroservicoItemPayload> itens;
}
