package com.penseprecifique.api.shared.domain.enums;

/** V0.18.0 — tipo do compromisso da Agenda (RN-NOVA-9, 11, 12, 13, 16, 18, 19). */
public enum TipoCompromisso {
    ENTREGA_ESTIMADA(OrigemCompromisso.ORCAMENTO),
    VALIDADE_ORCAMENTO(OrigemCompromisso.ORCAMENTO),
    SINAL_A_RECEBER(OrigemCompromisso.ORCAMENTO),
    SALDO_A_RECEBER(OrigemCompromisso.ORCAMENTO),
    INICIO_PRODUCAO(OrigemCompromisso.PRODUCAO),
    TERMINO_PRODUCAO(OrigemCompromisso.PRODUCAO),
    RECEBIMENTO_COMPRA(OrigemCompromisso.COMPRA),
    AVULSO(OrigemCompromisso.AVULSO);

    private final OrigemCompromisso origem;

    TipoCompromisso(OrigemCompromisso origem) {
        this.origem = origem;
    }

    public OrigemCompromisso origem() {
        return origem;
    }
}
