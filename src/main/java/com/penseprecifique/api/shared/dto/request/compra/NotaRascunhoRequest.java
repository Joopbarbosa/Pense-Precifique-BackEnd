package com.penseprecifique.api.shared.dto.request.compra;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.OrigemLigacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * V0.16.0 (#683, DT-NOVA-9) — pedido de rascunho a partir de uma nota lida. {@code notaLida} e
 * {@code assinatura} voltam exatamente como a leitura devolveu; o backend recusa se não baterem. As
 * escolhas da artesã referem-se aos itens só pela posição. O comprovante vai junto: o arquivo (parte
 * multipart) ou o {@code comprovanteLink} quando a leitura começou por câmera ou link do QR.
 */
public record NotaRascunhoRequest(
        @NotNull(message = "Informe a nota lida")
        @Valid
        NotaLida notaLida,

        @NotBlank(message = "Informe a assinatura da leitura")
        String assinatura,

        @NotNull(message = "Informe as escolhas dos itens")
        @Size(max = 500, message = "Itens demais na mesma nota")
        List<@Valid Escolha> escolhas,

        /** Obrigatória quando o emitente não é fornecedor cadastrado (RN-NOVA-10). */
        AcaoFornecedor fornecedor,

        @Size(max = 2000, message = "Link muito longo")
        String comprovanteLink
) {
    /**
     * Escolha da artesã para um item: ligar a um insumo com um fator de conversão (quantas unidades do
     * insumo vêm em uma unidade da nota) ou ignorar.
     */
    public record Escolha(
            @NotNull(message = "Informe a posição do item")
            @Min(value = 0, message = "Posição inválida")
            Integer posicao,
            UUID insumoId,
            BigDecimal fator,
            Boolean ignorar,
            /** #681 — de onde veio a ligação aceita (só registro no vínculo); nulo ou trocada à mão = MANUAL. */
            OrigemLigacao origem
    ) {
        public Escolha(Integer posicao, UUID insumoId, BigDecimal fator, Boolean ignorar) {
            this(posicao, insumoId, fator, ignorar, null);
        }

        public boolean ignorado() {
            return Boolean.TRUE.equals(ignorar);
        }
    }

    /** O sistema nunca cria nem altera cadastro sozinho: a artesã escolhe uma destas ações. */
    public enum AcaoFornecedor {
        ADICIONAR_PAPEL,
        CADASTRAR,
        SEM_FORNECEDOR
    }
}
