package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * V0.16.0 (#683, DT-NOVA-9) — resultado de {@code POST /compras/nota/leitura}: a nota lida, a assinatura
 * que a liga ao rascunho, a situação do emitente como fornecedor e a proposta de conciliação. Nada é gravado.
 * #681 (RN-NOVA-12 a 14) — cada item traz a ligação proposta e de onde veio; {@code avisos} traz avisos
 * gerais da conciliação (ex.: limite mensal de sugestões da IA atingido).
 */
public record NotaLeituraResponse(
        NotaLida nota,
        String assinatura,
        Instant expiraEm,
        FornecedorProposta fornecedor,
        /** Preenchido quando a nota já está num rascunho da usuária (RN-NOVA-5): a tela abre esse rascunho. */
        CompraRef rascunhoExistente,
        List<ItemConciliacao> itens,
        List<String> avisos
) {
    public NotaLeituraResponse(CompraRef rascunhoExistente) {
        this(null, null, null, null, rascunhoExistente, List.of(), List.of());
    }

    public enum SituacaoFornecedor {
        FORNECEDOR_CADASTRADO,
        SO_CLIENTE,
        NAO_CADASTRADO
    }

    public record FornecedorProposta(SituacaoFornecedor situacao, UUID fornecedorId, String nome, String cnpj) {}

    public record CompraRef(UUID id, String identificador) {}

    /** Origem da ligação proposta para um item (RN-NOVA-14). */
    public enum OrigemLigacao {
        VINCULO_SALVO,
        CASAMENTO_NOME,
        SUGESTAO_IA,
        SEM_LIGACAO
    }

    /** Insumo proposto ou candidato; {@code rascunho} participa da conciliação (RN-NOVA-12). */
    public record InsumoProposto(UUID id, String identificador, String nome, String marca, String unidade, boolean rascunho) {}

    /**
     * Item da nota com a proposta: {@code insumo} e {@code fator} quando ligado (vínculo salvo, nome ou
     * sugestão da IA); {@code ignorar} quando o vínculo salvo manda ignorar; {@code candidatos} quando mais de
     * um insumo casa pelo nome ou o vínculo aponta para insumo inativo ({@code aviso}).
     */
    public record ItemConciliacao(int posicao, String nome, BigDecimal quantidade, BigDecimal valorFinal,
                                  String unidade, OrigemLigacao origemLigacao, InsumoProposto insumo, BigDecimal fator,
                                  boolean ignorar, List<InsumoProposto> candidatos, String aviso) {}
}
