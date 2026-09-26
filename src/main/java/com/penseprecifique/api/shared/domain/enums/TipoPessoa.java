package com.penseprecifique.api.shared.domain.enums;

/**
 * #536/RN-NOVA-17 (V0.15.0) — tipo de pessoa do cadastro Clientes e Fornecedores. Define qual
 * documento é validado: CPF (FISICA), CNPJ alfanumérico (JURIDICA) ou texto livre (ESTRANGEIRO).
 */
public enum TipoPessoa {
    FISICA, JURIDICA, ESTRANGEIRO
}
