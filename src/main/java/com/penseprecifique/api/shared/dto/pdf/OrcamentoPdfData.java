package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OrcamentoPdfData {
    private String numeroFormatado;
    private String nomeEmpresa;
    private String emailEmpresa;
    private String telefoneEmpresa;
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
    private String subtotal;
    private String desconto;
    private String percentualDesconto;
    private String total;
    private String observacoes;
    private List<ItemPdfData> itens;
}
