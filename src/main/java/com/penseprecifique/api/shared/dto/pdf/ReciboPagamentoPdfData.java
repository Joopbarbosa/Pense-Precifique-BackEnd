package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReciboPagamentoPdfData {
    private String numeroFormatado;
    private String nomeCliente;
    private String telefoneCliente;
    private String emailCliente;
    private String nomeEmpresa;
    private String emailEmpresa;
    private String telefoneEmpresa;
    // V0.17.0 #965 (RN-NOVA-20): logo cadastrado da empresa; nulo quando não há logo
    private String logoUrlEmpresa;
    private String metodoPagamento;
    private String valorTotal;
    private String valorSinalPago;
    private String valorRestantePago;
    private String totalQuitado;
    private String dataEmissao;
    private String dataAprovacao;
    private String prazoProducao;
    private String inicioProducao;
    private String dataPagamento;
    private List<ItemPdfData> itens;
    private String observacoes;
}
