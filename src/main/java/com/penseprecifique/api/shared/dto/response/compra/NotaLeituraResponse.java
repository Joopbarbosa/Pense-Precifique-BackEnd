package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * V0.16.0 (#683, DT-NOVA-9) — resultado de {@code POST /compras/nota/leitura}: a nota lida, a assinatura
 * que a liga ao rascunho, a situação do emitente como fornecedor e a proposta de conciliação. Nada é gravado.
 * Nesta etapa todo item vem {@code SEM_LIGACAO}; a conciliação automática é da #681.
 */
public record NotaLeituraResponse(
        NotaLida nota,
        String assinatura,
        Instant expiraEm,
        FornecedorProposta fornecedor,
        /** Preenchido quando a nota já está num rascunho da usuária (RN-NOVA-5): a tela abre esse rascunho. */
        CompraRef rascunhoExistente,
        List<ItemConciliacao> itens
) {
    public enum SituacaoFornecedor {
        FORNECEDOR_CADASTRADO,
        SO_CLIENTE,
        NAO_CADASTRADO
    }

    public record FornecedorProposta(SituacaoFornecedor situacao, UUID fornecedorId, String nome, String cnpj) {}

    public record CompraRef(UUID id, String identificador) {}

    /** {@code origemLigacao}: SEM_LIGACAO nesta etapa (vínculo salvo, nome e IA chegam na #681). */
    public record ItemConciliacao(int posicao, String nome, BigDecimal quantidade, BigDecimal valorFinal,
                                  String unidade, String origemLigacao, UUID insumoId, BigDecimal fator) {}
}
