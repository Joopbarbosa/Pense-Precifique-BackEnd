package com.penseprecifique.api.shared.domain.enums;

/**
 * #491/RN-NOVA-15 — tipo fixo do método de pagamento configurável do Caixa/PDV. Não confundir com
 * o enum fixo antigo de forma de pagamento do orçamento (removido na V0.19.0, #1185) —
 * nomes iguais em módulos diferentes, sem relação entre si (achado registrado em
 * decisoes-config-perfil.md).
 */
public enum TipoMetodoPagamento {
    DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, OUTRO
}
