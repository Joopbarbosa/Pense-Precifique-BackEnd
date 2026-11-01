package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** V0.19.0 #382 (RN-NOVA-44) — payload do tipo {@code orcamento-descritivo}: sem total, desconto, sinal nem pagamento. */
@Data
@Builder
public class PdfMicroservicoOrcamentoDescritivoPayload {
    private PdfMicroservicoEmpresaPayload empresa;
    private Documento documento;

    @Data
    @Builder
    public static class Documento {
        private String numeroFormatado;
        private String titulo;
        private String nomeCliente;
        private String telefoneCliente;
        private String dataEmissao;
        private String observacoes;
        private List<Opcao> opcoes;
    }

    @Data
    @Builder
    public static class Opcao {
        private String nome;
        private String quantidadeUnidade;
        private String precoReferencia;
    }
}
