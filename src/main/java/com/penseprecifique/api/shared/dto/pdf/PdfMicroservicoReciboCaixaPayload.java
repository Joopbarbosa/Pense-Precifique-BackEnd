package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** V0.19.0 #1170 (RN-NOVA-33) — payload do tipo {@code recibo-caixa} do microsserviço de PDF. */
@Data
@Builder
public class PdfMicroservicoReciboCaixaPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private Documento documento;

    @Data
    @Builder
    public static class Documento {
        private String numeroFormatado;
        private String dataVenda;
        private String nomeCliente;
        private String telefoneCliente;
        private List<PdfMicroservicoItemPayload> itens;
        private String subtotal;
        private String desconto;
        private String total;
        private List<Pagamento> pagamentos;
        private String troco;
    }

    @Data
    @Builder
    public static class Pagamento {
        private String metodo;
        private String valor;
    }
}
