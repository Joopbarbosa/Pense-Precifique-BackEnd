package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** #545/RN-NOVA-11 (V0.15.0) — tudo formatado no Java (PdfMapper Pattern); statusCodigo decide o destaque de RASCUNHO/CANCELADA. */
@Data
@Builder
public class PdfMicroservicoDocumentoCompraPayload {
    private String numeroFormatado;
    private String status;
    private String statusCodigo;
    private String dataCompra;
    private String fornecedor;
    private boolean multiplosFornecedores;
    private String pagamento;
    private String total;
    private String observacoes;
    private String dataCancelamento;
    private String observacaoCancelamento;
    private List<PdfMicroservicoItemCompraPayload> itens;
    /** #576/RN-NOVA-28 — com algum desconto, o PDF mostra Preço cheio / Desconto / Preço pago. */
    private boolean temDesconto;
    private String totalCheio;
    private String totalDescontos;
    /** Desconto na nota como digitado, ex. "5% (R$ 3,55)"; nulo sem desconto na nota. */
    private String descontoNota;
}
