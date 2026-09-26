package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

/** #545 (V0.15.0) — linha do PDF da compra; valor não informado vem "—". */
@Data
@Builder
public class PdfMicroservicoItemCompraPayload {
    private String insumo;
    private String fornecedor;
    private String quantidade;
    private String precoTotal;
    private String precoUnitario;
}
