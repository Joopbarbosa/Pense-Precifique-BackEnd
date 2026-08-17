package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReciboPdfData {
    private String numeroFormatado;
    private String nomeCliente;
    private String nomeEmpresa;
    private String emailEmpresa;
    private String telefoneEmpresa;
    private String metodoRecebido;
    private String valorRecebido;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
    private String dataEstorno;
    private String percentualMulta;
    private String valorMulta;
    private String motivo;
    // Campos abaixo só populados por toReciboPdfData() (recibo-sinal) — seção "Detalhes do
    // pedido"/"Próximos passos" do mock (P-F007b), ausentes em toReciboPdfDataMulta/Estorno.
    private List<ItemPdfData> itens;
    private String valorTotalPedido;
    private String percentualSinal;
    private String restante;
}
