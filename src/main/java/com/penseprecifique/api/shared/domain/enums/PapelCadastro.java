package com.penseprecifique.api.shared.domain.enums;

/**
 * #536/RN-NOVA-1 (V0.15.0) — papel de um registro do cadastro Clientes e Fornecedores. Não é
 * persistido como coluna (são as flags {@code eh_cliente}/{@code eh_fornecedor}); serve de filtro
 * em GET /clientes e de parâmetro da validação de vínculo (RN-NOVA-3).
 */
public enum PapelCadastro {
    CLIENTE, FORNECEDOR
}
