package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PdfMicroservicoDocumentoReciboSinalPayload {
    private String numeroFormatado;
    private String nomeCliente;
    private String telefoneCliente;
    private String emailCliente;
    private String metodoRecebido;
    private String valorRecebido;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
    // P-F007b — restaura "Detalhes do pedido"/"Próximos passos" do mock, cortadas em #248 por
    // falta de dado no schema original.
    private List<PdfMicroservicoItemPayload> itens;
    private String valorTotalPedido;
    private String percentualSinal;
    private String restante;
}
