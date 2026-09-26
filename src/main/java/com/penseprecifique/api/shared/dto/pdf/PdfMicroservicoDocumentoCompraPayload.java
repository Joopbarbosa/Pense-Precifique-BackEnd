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
}
