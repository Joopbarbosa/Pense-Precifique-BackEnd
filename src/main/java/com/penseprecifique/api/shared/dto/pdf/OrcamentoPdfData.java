package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OrcamentoPdfData {
    private String numeroFormatado;
    private String status;
    private String nomeEmpresa;
    private String emailEmpresa;
    private String telefoneEmpresa;
    // V0.17.0 #965 (RN-NOVA-20): logo cadastrado da empresa; nulo quando não há logo
    private String logoUrlEmpresa;
    private String nomeCliente;
    private String telefoneCliente;
    private String emailCliente;
    private String dataEmissao;
    private String dataValidade;
    private String prazoProducao;
    private String inicioProducao;
    private String metodoPagamento;
    private boolean sinalAtivo;
    private String valorSinal;
    private String restanteAposSinal;
    private String percentualSinal;
    // V0.18.0 #1105: data limite do sinal já formatada (dd/MM/yyyy); nulo quando não informada
    private String sinalAte;
    private String subtotal;
    private String desconto;
    private String percentualDesconto;
    private String total;
    private String observacoes;
    private List<ItemPdfData> itens;
}
