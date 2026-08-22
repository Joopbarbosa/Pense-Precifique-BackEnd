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
    private String dataEmissao;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
    private String dataEstorno;
    private String percentualMulta;
    private String valorMulta;
    private String motivo;
    private String dataCancelamento;
    private String telefoneCliente;
    private String emailCliente;
    // itens populados por toReciboPdfData() (recibo-sinal), toReciboPdfDataMulta() e
    // toReciboPdfDataEstorno() (P-F008/P-B004, Seção 5 "Detalhes do produto").
    private List<ItemPdfData> itens;
    private String valorTotalPedido;
    private String percentualSinal;
    private String restante;
    private String observacoes;
}
