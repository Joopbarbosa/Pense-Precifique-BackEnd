package com.penseprecifique.api.shared.domain.enums;

/**
 * #491/RN-NOVA-15 — tipo fixo do método de pagamento configurável do Caixa/PDV. Não confundir com
 * {@link MetodoPagamento} (enum já existente, usado só por {@code Orcamento.metodoPagamento}) —
 * nomes iguais em módulos diferentes, sem relação entre si (achado registrado em
 * decisoes-config-perfil.md).
 */
public enum TipoMetodoPagamento {
    DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, OUTRO
}
