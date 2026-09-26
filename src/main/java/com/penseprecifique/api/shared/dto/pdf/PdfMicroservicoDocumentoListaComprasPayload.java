package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** #547/RN-NOVA-14 (V0.15.0) — sempre do retrato LST-N; grupos por fornecedor sugerido, "Sem fornecedor" no fim. */
@Data
@Builder
public class PdfMicroservicoDocumentoListaComprasPayload {
    private String numeroFormatado;
    private String dataGeracao;
    private int quantidadeItens;
    private List<Grupo> grupos;

    @Data
    @Builder
    public static class Grupo {
        private String fornecedor;
        private List<Item> itens;
    }

    @Data
    @Builder
    public static class Item {
        private String insumo;
        private String unidade;
        private String quantidade;
        private String precoReferencia;
    }
}
